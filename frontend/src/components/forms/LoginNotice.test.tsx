import { render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { LoginNotice } from "./LoginNotice"

const nav = vi.hoisted(() => ({ query: "" }))
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(nav.query) }))

beforeEach(() => {
  nav.query = ""
})

describe("LoginNotice", () => {
  it("shows_the_password_changed_notice", () => {
    nav.query = "notice=password-changed"
    render(<LoginNotice />)
    expect(screen.getByRole("status")).toHaveTextContent("Kata sandi berhasil diganti")
  })

  it("ignores_unknown_notice_values_and_never_echoes_them", () => {
    nav.query = "notice=<b>pwned</b>"
    const { container } = render(<LoginNotice />)
    expect(container).toBeEmptyDOMElement()
  })

  it("renders_nothing_without_a_notice", () => {
    const { container } = render(<LoginNotice />)
    expect(container).toBeEmptyDOMElement()
  })
})
