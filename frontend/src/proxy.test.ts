import { NextRequest } from "next/server"
import { describe, expect, it } from "vitest"

import { SESSION_HINT_COOKIE } from "@/lib/auth/constants"

import { proxy } from "./proxy"

function run(path: string, withHint: boolean) {
  const headers = withHint ? { cookie: `${SESSION_HINT_COOKIE}=1` } : undefined
  return proxy(new NextRequest(`http://localhost:3000${path}`, { headers }))
}

describe("proxy", () => {
  it("redirects_protected_pages_to_login_without_session_hint", () => {
    for (const path of ["/dashboard", "/tasks/12", "/"]) {
      const response = run(path, false)
      expect(response.status).toBe(307)
      expect(new URL(response.headers.get("location")!).pathname).toBe("/login")
    }
  })

  it("lets_protected_pages_through_with_session_hint", () => {
    expect(run("/tasks", true).headers.get("location")).toBeNull()
  })

  it("redirects_auth_pages_to_dashboard_with_session_hint", () => {
    for (const path of ["/login", "/register"]) {
      const response = run(path, true)
      expect(new URL(response.headers.get("location")!).pathname).toBe("/dashboard")
    }
  })

  it("lets_auth_pages_through_without_session_hint", () => {
    expect(run("/login", false).headers.get("location")).toBeNull()
  })
})
