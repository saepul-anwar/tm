import { clearSessionHint, setSessionHint } from "@/lib/auth/session-hint"

export type FieldDetail = { field: string; message: string }

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details: FieldDetail[] = [],
  ) {
    super(message)
    this.name = "ApiError"
  }
}

type Envelope<T> = {
  success: boolean
  data?: T
  error?: { code?: string; message?: string; details?: FieldDetail[] | null }
}

type TokenPayload = { accessToken: string; expiresIn: number }

// Access token lives in memory only: never localStorage/sessionStorage/cookies.
let accessToken: string | null = null
let refreshInFlight: Promise<string | null> | null = null
const expiredListeners = new Set<() => void>()

export const getAccessToken = () => accessToken

export function setAccessToken(token: string | null): void {
  accessToken = token
  if (token) setSessionHint()
  else clearSessionHint()
}

/** Called when a refresh is rejected, i.e. the session is gone. Returns an unsubscribe function. */
export function onSessionExpired(listener: () => void): () => void {
  expiredListeners.add(listener)
  return () => expiredListeners.delete(listener)
}

// Endpoints whose 401 means "bad credentials/no session", never "access token expired".
const NO_REFRESH_PATHS = ["/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout"]

async function toApiError(response: Response): Promise<ApiError> {
  const body = (await response.json().catch(() => null)) as Envelope<unknown> | null
  return new ApiError(
    response.status,
    body?.error?.code ?? "UNKNOWN_ERROR",
    body?.error?.message ?? "Terjadi kesalahan. Coba lagi.",
    body?.error?.details ?? [],
  )
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set("Accept", "application/json")
  if (typeof init.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
  }
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`)
  try {
    return await fetch(path, { ...init, headers, credentials: "same-origin" })
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "Tidak dapat terhubung ke server. Periksa koneksi Anda.")
  }
}

async function requestRefresh(): Promise<string | null> {
  const response = await send("/api/auth/refresh", { method: "POST" })
  if (response.status === 401 || response.status === 403) {
    setAccessToken(null)
    expiredListeners.forEach((listener) => listener())
    return null
  }
  if (!response.ok) throw await toApiError(response)
  const body = (await response.json()) as Envelope<TokenPayload>
  const token = body.data?.accessToken ?? null
  setAccessToken(token)
  return token
}

/** Single-flight: concurrent callers share one refresh (the refresh token is single-use). */
export function refreshAccessToken(): Promise<string | null> {
  refreshInFlight ??= requestRefresh().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await send(path, init)

  if (response.status === 401 && !NO_REFRESH_PATHS.includes(path)) {
    const token = await refreshAccessToken()
    if (token) response = await send(path, init)
  }

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T

  const body = (await response.json()) as Envelope<T>
  if (!body.success) throw await toApiError(response)
  return body.data as T
}
