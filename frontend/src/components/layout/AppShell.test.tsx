import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { AppShell } from "./AppShell"

const state = vi.hoisted(() => ({ pathname: "/dashboard", logout: vi.fn() }))

vi.mock("next/navigation", () => ({ usePathname: () => state.pathname }))
vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({
    status: "authenticated",
    user: { id: 1, fullName: "Budi Santoso", email: "budi@example.com", timezone: "Asia/Jakarta" },
    logout: state.logout,
  }),
}))

describe("AppShell", () => {
  it("renders_five_nav_items_in_order", () => {
    render(<AppShell>content</AppShell>)

    const nav = screen.getByRole("navigation", { name: "Navigasi utama" })
    const links = within(nav).getAllByRole("link")

    expect(links.map((link) => link.textContent)).toEqual([
      "Dashboard",
      "My Tasks",
      "Calendar",
      "Completed",
      "Settings",
    ])
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      "/dashboard",
      "/tasks",
      "/calendar",
      "/completed",
      "/settings",
    ])
  })

  it("mobile_menu_button_opens_drawer", async () => {
    const user = userEvent.setup()
    render(<AppShell>content</AppShell>)
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Buka menu" }))

    const drawer = screen.getByRole("dialog", { name: "Menu navigasi" })
    expect(within(drawer).getAllByRole("link")).toHaveLength(5)

    await user.click(within(drawer).getByRole("button", { name: "Tutup menu" }))
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  })

  it("logout_button_calls_logout", async () => {
    const user = userEvent.setup()
    state.pathname = "/dashboard"
    state.logout.mockReset()
    render(<AppShell>content</AppShell>)

    await user.click(screen.getAllByRole("button", { name: "Keluar" })[0])

    expect(state.logout).toHaveBeenCalledTimes(1)
  })

  it.each(["/login", "/register"])("hides_navigation_and_logout_on_%s", (path) => {
    state.pathname = path
    render(<AppShell>content</AppShell>)

    expect(screen.queryByRole("navigation")).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Keluar" })).not.toBeInTheDocument()
    expect(screen.getByText("content")).toBeInTheDocument()
    state.pathname = "/dashboard"
  })
})
