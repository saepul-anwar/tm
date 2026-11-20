import { act, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { getAccessToken, setAccessToken } from "@/lib/api"

import { AuthProvider, useAuth } from "./AuthProvider"
import { SESSION_HINT_COOKIE } from "./constants"

const nav = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn(), pathname: "/dashboard" }))

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: nav.replace, push: nav.push }),
  usePathname: () => nav.pathname,
}))

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } })
}
const ok = (data: unknown) => json(200, { success: true, data })
const user = { id: 1, fullName: "Budi Santoso", email: "budi@example.com", timezone: "Asia/Jakarta" }

let fetchMock: ReturnType<typeof vi.fn>

function Probe() {
  const auth = useAuth()
  return (
    <div>
      <span data-testid="status">{auth.status}</span>
      <span data-testid="name">{auth.user?.fullName ?? "-"}</span>
      <button onClick={() => void auth.login("budi@example.com", "rahasia123")}>login</button>
      <button onClick={() => void auth.logout()}>logout</button>
    </div>
  )
}

const renderProvider = () =>
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal("fetch", fetchMock)
  setAccessToken(null)
  nav.replace.mockClear()
  nav.push.mockClear()
  nav.pathname = "/dashboard"
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = `${SESSION_HINT_COOKIE}=; Path=/; Max-Age=0`
})

describe("AuthProvider", () => {
  it("AuthProvider_restores_session_via_refresh_on_load", async () => {
    document.cookie = `${SESSION_HINT_COOKIE}=1; Path=/`
    fetchMock.mockImplementation(async (url: string) =>
      url === "/api/auth/refresh" ? ok({ accessToken: "tok", expiresIn: 900 }) : ok(user),
    )

    renderProvider()
    expect(screen.getByTestId("status")).toHaveTextContent("loading")

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"))
    expect(screen.getByTestId("name")).toHaveTextContent("Budi Santoso")
    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/refresh")
    expect(fetchMock.mock.calls[1][0]).toBe("/api/auth/me")
    expect(getAccessToken()).toBe("tok")
  })

  it("becomes_anonymous_without_calling_refresh_when_there_is_no_session_hint", async () => {
    renderProvider()

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"))
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("becomes_anonymous_and_clears_hint_when_refresh_is_rejected", async () => {
    document.cookie = `${SESSION_HINT_COOKIE}=1; Path=/`
    fetchMock.mockResolvedValueOnce(
      json(401, { success: false, error: { code: "UNAUTHENTICATED", message: "x", details: [] } }),
    )

    renderProvider()

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"))
    expect(document.cookie).not.toContain(SESSION_HINT_COOKIE)
  })

  it("redirects_anonymous_users_on_protected_pages_to_login", async () => {
    nav.pathname = "/tasks"
    renderProvider()
    await waitFor(() => expect(nav.replace).toHaveBeenCalledWith("/login"))
  })

  it("redirects_authenticated_users_on_auth_pages_to_dashboard", async () => {
    nav.pathname = "/login"
    document.cookie = `${SESSION_HINT_COOKIE}=1; Path=/`
    fetchMock.mockImplementation(async (url: string) =>
      url === "/api/auth/refresh" ? ok({ accessToken: "tok", expiresIn: 900 }) : ok(user),
    )

    renderProvider()

    await waitFor(() => expect(nav.replace).toHaveBeenCalledWith("/dashboard"))
  })

  it("login_stores_token_in_memory_and_loads_user", async () => {
    nav.pathname = "/login"
    const actor = userEvent.setup()
    fetchMock.mockImplementation(async (url: string) =>
      url === "/api/auth/login" ? ok({ accessToken: "tok-login", expiresIn: 900 }) : ok(user),
    )
    renderProvider()
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"))

    await actor.click(screen.getByText("login"))

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"))
    expect(getAccessToken()).toBe("tok-login")
    const loginCall = fetchMock.mock.calls.find((call) => call[0] === "/api/auth/login")!
    expect(JSON.parse((loginCall[1] as RequestInit).body as string)).toEqual({
      email: "budi@example.com",
      password: "rahasia123",
    })
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain("tok-login")
  })

  it("logout_clears_token_and_redirects_to_login", async () => {
    const actor = userEvent.setup()
    document.cookie = `${SESSION_HINT_COOKIE}=1; Path=/`
    fetchMock.mockImplementation(async (url: string) => {
      if (url === "/api/auth/refresh") return ok({ accessToken: "tok", expiresIn: 900 })
      if (url === "/api/auth/logout") return new Response(null, { status: 204 })
      return ok(user)
    })
    renderProvider()
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"))

    await act(async () => {
      await actor.click(screen.getByText("logout"))
    })

    expect(fetchMock.mock.calls.some((call) => call[0] === "/api/auth/logout")).toBe(true)
    expect(getAccessToken()).toBeNull()
    expect(screen.getByTestId("status")).toHaveTextContent("anonymous")
    expect(document.cookie).not.toContain(SESSION_HINT_COOKIE)
    expect(nav.replace).toHaveBeenCalledWith("/login")
  })

  it("logout_still_clears_local_session_when_the_server_call_fails", async () => {
    const actor = userEvent.setup()
    document.cookie = `${SESSION_HINT_COOKIE}=1; Path=/`
    fetchMock.mockImplementation(async (url: string) => {
      if (url === "/api/auth/refresh") return ok({ accessToken: "tok", expiresIn: 900 })
      if (url === "/api/auth/logout") throw new TypeError("offline")
      return ok(user)
    })
    renderProvider()
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"))

    await act(async () => {
      await actor.click(screen.getByText("logout"))
    })

    expect(getAccessToken()).toBeNull()
    expect(nav.replace).toHaveBeenCalledWith("/login")
  })
})
