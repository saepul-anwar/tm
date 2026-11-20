"use client"

import { usePathname, useRouter } from "next/navigation"
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react"

import {
  apiFetch,
  onSessionExpired,
  refreshAccessToken,
  setAccessToken,
} from "@/lib/api"

import { isPublicPath } from "./constants"
import type { RegisterValues } from "./schemas"
import { hasSessionHint } from "./session-hint"

export type AuthUser = { id: number; fullName: string; email: string; timezone: string }
export type AuthStatus = "loading" | "authenticated" | "anonymous"
export type RegisterInput = Pick<RegisterValues, "fullName" | "email" | "password" | "confirmPassword">

type AuthContextValue = {
  status: AuthStatus
  user: AuthUser | null
  login: (email: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: (redirectTo?: string) => Promise<void>
  /** Re-reads /api/auth/me so name/email/timezone shown elsewhere stay current. Keeps the old user on failure. */
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

const postJson = (body: unknown): RequestInit => ({ method: "POST", body: JSON.stringify(body) })

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const [status, setStatus] = useState<AuthStatus>("loading")
  const [user, setUser] = useState<AuthUser | null>(null)

  const becomeAnonymous = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    setStatus("anonymous")
  }, [])

  const loadUser = useCallback(async () => {
    setUser(await apiFetch<AuthUser>("/api/auth/me"))
    setStatus("authenticated")
  }, [])

  useEffect(() => {
    let active = true
    const unsubscribe = onSessionExpired(() => active && becomeAnonymous())

    async function restore() {
      // No hint means no session was ever established in this browser: skip the
      // refresh call (it is rate limited and would only produce a 401).
      if (!hasSessionHint()) return becomeAnonymous()
      try {
        if (!(await refreshAccessToken())) return
        await loadUser()
      } catch {
        if (active) becomeAnonymous()
      }
    }
    void restore()

    return () => {
      active = false
      unsubscribe()
    }
  }, [becomeAnonymous, loadUser])

  // UX guard only; the backend enforces authorization on every request.
  useEffect(() => {
    if (status === "anonymous" && !isPublicPath(pathname)) router.replace("/login")
    if (status === "authenticated" && isPublicPath(pathname)) router.replace("/dashboard")
  }, [status, pathname, router])

  const login = useCallback(
    async (email: string, password: string) => {
      const tokens = await apiFetch<{ accessToken: string }>("/api/auth/login", postJson({ email, password }))
      setAccessToken(tokens.accessToken)
      try {
        await loadUser()
      } catch (error) {
        becomeAnonymous()
        throw error
      }
    },
    [becomeAnonymous, loadUser],
  )

  const register = useCallback(
    async (input: RegisterInput) => {
      await apiFetch("/api/auth/register", postJson(input))
      await login(input.email, input.password)
    },
    [login],
  )

  const refreshUser = useCallback(async () => {
    try {
      await loadUser()
    } catch {
      // A failed refresh must not log the user out; an expired session is handled by onSessionExpired.
    }
  }, [loadUser])

  const logout = useCallback(async (redirectTo = "/login") => {
    try {
      await apiFetch<void>("/api/auth/logout", { method: "POST" })
    } catch {
      // Clear the local session even if the server could not be reached.
    }
    becomeAnonymous()
    router.replace(redirectTo)
  }, [becomeAnonymous, router])

  const value = useMemo(
    () => ({ status, user, login, register, logout, refreshUser }),
    [status, user, login, register, logout, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error("useAuth must be used inside <AuthProvider>")
  return context
}
