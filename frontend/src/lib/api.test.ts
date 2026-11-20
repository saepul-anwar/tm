import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError, apiFetch, getAccessToken, setAccessToken } from "./api"

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const ok = (data: unknown) => json(200, { success: true, data })
const tokenResponse = (token: string) => ok({ accessToken: token, expiresIn: 900 })
const unauthenticated = () =>
  json(401, { success: false, error: { code: "UNAUTHENTICATED", message: "Sesi berakhir.", details: [] } })

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal("fetch", fetchMock)
  setAccessToken(null)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function authHeader(call: unknown[]): string | null {
  return new Headers((call[1] as RequestInit).headers).get("Authorization")
}

describe("apiFetch", () => {
  it("unwraps_data_and_sends_the_in_memory_token", async () => {
    setAccessToken("tok-1")
    fetchMock.mockResolvedValueOnce(ok({ hello: "dunia" }))

    await expect(apiFetch<{ hello: string }>("/api/ping")).resolves.toEqual({ hello: "dunia" })
    expect(authHeader(fetchMock.mock.calls[0])).toBe("Bearer tok-1")
  })

  it("apiFetch_retries_once_after_refresh_on_401", async () => {
    setAccessToken("expired")
    fetchMock
      .mockResolvedValueOnce(unauthenticated())
      .mockResolvedValueOnce(tokenResponse("fresh"))
      .mockResolvedValueOnce(ok({ n: 1 }))

    await expect(apiFetch("/api/things")).resolves.toEqual({ n: 1 })

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1][0]).toBe("/api/auth/refresh")
    expect((fetchMock.mock.calls[1][1] as RequestInit).method).toBe("POST")
    expect(authHeader(fetchMock.mock.calls[2])).toBe("Bearer fresh")
    expect(getAccessToken()).toBe("fresh")
  })

  it("does_not_loop_when_the_retry_is_also_401", async () => {
    setAccessToken("expired")
    fetchMock
      .mockResolvedValueOnce(unauthenticated())
      .mockResolvedValueOnce(tokenResponse("fresh"))
      .mockResolvedValueOnce(unauthenticated())

    await expect(apiFetch("/api/things")).rejects.toMatchObject({ status: 401, code: "UNAUTHENTICATED" })
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it("concurrent_401s_trigger_only_one_refresh", async () => {
    setAccessToken("expired")
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (url === "/api/auth/refresh") {
        await new Promise((resolve) => setTimeout(resolve, 10))
        return tokenResponse("fresh")
      }
      return authHeader([url, init]) === "Bearer fresh" ? ok({ url }) : unauthenticated()
    })

    const results = await Promise.all([apiFetch("/api/a"), apiFetch("/api/b"), apiFetch("/api/c")])

    expect(results).toEqual([{ url: "/api/a" }, { url: "/api/b" }, { url: "/api/c" }])
    const refreshCalls = fetchMock.mock.calls.filter((call) => call[0] === "/api/auth/refresh")
    expect(refreshCalls).toHaveLength(1)
  })

  it("clears_the_token_and_throws_when_refresh_is_rejected", async () => {
    setAccessToken("expired")
    fetchMock.mockResolvedValueOnce(unauthenticated()).mockResolvedValueOnce(unauthenticated())

    await expect(apiFetch("/api/things")).rejects.toBeInstanceOf(ApiError)
    expect(getAccessToken()).toBeNull()
  })

  it("does_not_refresh_for_login_401", async () => {
    fetchMock.mockResolvedValueOnce(
      json(401, { success: false, error: { code: "UNAUTHENTICATED", message: "Email atau kata sandi salah.", details: [] } }),
    )

    await expect(apiFetch("/api/auth/login", { method: "POST", body: "{}" })).rejects.toMatchObject({
      status: 401,
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("throws_ApiError_with_code_message_and_field_details", async () => {
    fetchMock.mockResolvedValueOnce(
      json(400, {
        success: false,
        error: {
          code: "VALIDATION_ERROR",
          message: "Input tidak valid.",
          details: [{ field: "email", message: "Format email tidak valid." }],
        },
      }),
    )

    const error = await apiFetch("/api/auth/register", { method: "POST", body: "{}" }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 400,
      code: "VALIDATION_ERROR",
      message: "Input tidak valid.",
      details: [{ field: "email", message: "Format email tidak valid." }],
    })
  })

  it("returns_undefined_for_204", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    await expect(apiFetch("/api/auth/logout", { method: "POST" })).resolves.toBeUndefined()
  })

  it("maps_network_failure_to_ApiError", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"))
    await expect(apiFetch("/api/ping")).rejects.toMatchObject({ code: "NETWORK_ERROR" })
  })

  it("never_persists_the_token_in_web_storage", async () => {
    setAccessToken("secret-token")
    expect(JSON.stringify({ ...localStorage })).not.toContain("secret-token")
    expect(JSON.stringify({ ...sessionStorage })).not.toContain("secret-token")
    expect(document.cookie).not.toContain("secret-token")
  })
})
