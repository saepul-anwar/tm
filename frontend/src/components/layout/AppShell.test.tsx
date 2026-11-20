import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { AppShell } from "./AppShell"

vi.mock("next/navigation", () => ({ usePathname: () => "/dashboard" }))

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
})
