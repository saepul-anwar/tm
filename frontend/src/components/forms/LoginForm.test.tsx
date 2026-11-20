import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api"

import { LoginForm } from "./LoginForm"

// A plain function (not vi.fn) records calls: vitest 5 reports promises rejected by
// a spy as unhandled even when the component awaits and handles them.
const loginCalls: unknown[][] = []
let loginImpl: () => Promise<void> = async () => undefined
const login = (...args: unknown[]) => {
  loginCalls.push(args)
  return loginImpl()
}

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({ status: "anonymous", user: null, login }),
}))
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }))

beforeEach(() => {
  loginCalls.length = 0
  loginImpl = async () => undefined
})

describe("LoginForm", () => {
  it("LoginForm_shows_field_errors_and_blocks_submit", async () => {
    const actor = userEvent.setup()
    render(<LoginForm />)

    await actor.type(screen.getByLabelText("Email"), "bukan-email")
    await actor.click(screen.getByRole("button", { name: "Masuk" }))

    expect(await screen.findByText("Format email tidak valid.")).toBeInTheDocument()
    expect(screen.getByText("Kata sandi wajib diisi.")).toBeInTheDocument()
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true")
    expect(loginCalls).toHaveLength(0)
  })

  it("LoginForm_shows_generic_error_on_401", async () => {
    const actor = userEvent.setup()
    loginImpl = async () => {
      throw new ApiError(401, "UNAUTHENTICATED", "Pesan server apa pun.", [])
    }
    render(<LoginForm />)

    await actor.type(screen.getByLabelText("Email"), "budi@example.com")
    await actor.type(screen.getByLabelText("Kata sandi"), "salah12345")
    await actor.click(screen.getByRole("button", { name: "Masuk" }))

    expect(await screen.findByRole("alert")).toHaveTextContent("Email atau kata sandi salah.")
    expect(loginCalls).toEqual([["budi@example.com", "salah12345"]])
  })

  it("shows_rate_limit_message_on_429", async () => {
    const actor = userEvent.setup()
    loginImpl = async () => {
      throw new ApiError(429, "RATE_LIMITED", "x", [])
    }
    render(<LoginForm />)

    await actor.type(screen.getByLabelText("Email"), "budi@example.com")
    await actor.type(screen.getByLabelText("Kata sandi"), "salah12345")
    await actor.click(screen.getByRole("button", { name: "Masuk" }))

    expect(await screen.findByRole("alert")).toHaveTextContent("Terlalu banyak percobaan")
  })

  it("maps_backend_field_details_onto_fields", async () => {
    const actor = userEvent.setup()
    loginImpl = async () => {
      throw new ApiError(400, "VALIDATION_ERROR", "Input tidak valid.", [
        { field: "email", message: "Email terlalu panjang." },
      ])
    }
    render(<LoginForm />)

    await actor.type(screen.getByLabelText("Email"), "budi@example.com")
    await actor.type(screen.getByLabelText("Kata sandi"), "rahasia123")
    await actor.click(screen.getByRole("button", { name: "Masuk" }))

    expect(await screen.findByText("Email terlalu panjang.")).toBeInTheDocument()
  })

  it("disables_the_button_while_submitting", async () => {
    const actor = userEvent.setup()
    let resolve!: () => void
    loginImpl = () => new Promise<void>((r) => (resolve = r))
    render(<LoginForm />)

    await actor.type(screen.getByLabelText("Email"), "budi@example.com")
    await actor.type(screen.getByLabelText("Kata sandi"), "rahasia123")
    await actor.click(screen.getByRole("button", { name: "Masuk" }))

    await waitFor(() => expect(screen.getByRole("button", { name: "Memproses..." })).toBeDisabled())
    resolve()
  })
})
