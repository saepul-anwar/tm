import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api"

import { RegisterForm } from "./RegisterForm"

// Plain function instead of vi.fn: vitest 5 reports promises rejected by a spy as unhandled
// even when the component awaits and handles them.
const registerCalls: unknown[][] = []
let registerImpl: () => Promise<void> = async () => undefined
const register = (...args: unknown[]) => {
  registerCalls.push(args)
  return registerImpl()
}

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({ status: "anonymous", user: null, register }),
}))
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }))

beforeEach(() => {
  registerCalls.length = 0
  registerImpl = async () => undefined
})

async function fill(actor: ReturnType<typeof userEvent.setup>, overrides: Partial<Record<string, string>> = {}) {
  const values = {
    "Nama lengkap": "Budi Santoso",
    Email: "budi@example.com",
    "Kata sandi": "rahasia123",
    "Konfirmasi kata sandi": "rahasia123",
    ...overrides,
  }
  for (const [label, value] of Object.entries(values)) {
    await actor.type(screen.getByLabelText(label), value as string)
  }
  await actor.click(screen.getByRole("button", { name: "Daftar" }))
}

describe("RegisterForm", () => {
  it("shows_mismatch_error_and_blocks_submit", async () => {
    const actor = userEvent.setup()
    render(<RegisterForm />)

    await fill(actor, { "Konfirmasi kata sandi": "lain12345" })

    expect(await screen.findByText("Konfirmasi kata sandi tidak sama.")).toBeInTheDocument()
    expect(registerCalls).toHaveLength(0)
  })

  it("submits_valid_values", async () => {
    const actor = userEvent.setup()
        render(<RegisterForm />)

    await fill(actor)

    expect(registerCalls).toEqual([
      [
        {
          fullName: "Budi Santoso",
          email: "budi@example.com",
          password: "rahasia123",
          confirmPassword: "rahasia123",
        },
      ],
    ])
  })

  it("maps_duplicate_email_conflict_onto_the_email_field", async () => {
    const actor = userEvent.setup()
    registerImpl = async () => {
      throw new ApiError(409, "CONFLICT", "Email sudah terdaftar.", [{ field: "email", message: "Email sudah terdaftar." }])
    }
    render(<RegisterForm />)

    await fill(actor)

    expect(await screen.findByText("Email sudah terdaftar.")).toBeInTheDocument()
  })

  it("shows_rate_limit_message_on_429", async () => {
    const actor = userEvent.setup()
    registerImpl = async () => {
      throw new ApiError(429, "RATE_LIMITED", "x", [])
    }
    render(<RegisterForm />)

    await fill(actor)

    expect(await screen.findByRole("alert")).toHaveTextContent("Terlalu banyak percobaan")
  })
})
