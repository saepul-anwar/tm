import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { errorEnvelope, okEnvelope } from "@/features/tasks/test-fixtures"

import { PasswordForm } from "./PasswordForm"
import { makeProfile } from "./test-fixtures"

const auth = vi.hoisted(() => ({ refreshUser: vi.fn(), logout: vi.fn() }))
vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: () => auth }))

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  auth.logout.mockReset().mockResolvedValue(undefined)
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

async function fill(actor: ReturnType<typeof userEvent.setup>, current: string, next: string, confirm = next) {
  for (const [label, value] of [
    ["Kata sandi saat ini", current],
    ["Kata sandi baru", next],
    ["Konfirmasi kata sandi baru", confirm],
  ] as const) {
    await actor.click(screen.getByLabelText(label))
    await actor.paste(value)
  }
  await actor.click(screen.getByRole("button", { name: "Ganti kata sandi" }))
}

describe("PasswordForm", () => {
  it("rejects_30_emoji_password_over_72_bytes", async () => {
    const actor = userEvent.setup()
    render(<PasswordForm />)

    await fill(actor, "lama12345", "😀".repeat(30))

    expect(await screen.findByText(/terlalu panjang \(maksimal 72 byte/)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("requires_letter_digit_and_matching_confirmation", async () => {
    const actor = userEvent.setup()
    render(<PasswordForm />)

    await fill(actor, "lama12345", "hanyahuruf", "beda")

    expect(await screen.findByText("Kata sandi harus memuat minimal 1 angka.")).toBeInTheDocument()
    expect(screen.getByText("Konfirmasi kata sandi tidak sama.")).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("success_shows_message_then_logs_out_to_login_with_notice", async () => {
    const actor = userEvent.setup()
    fetchMock.mockResolvedValueOnce(okEnvelope(makeProfile()))
    render(<PasswordForm />)

    await fill(actor, "lama12345", "baru67890")

    expect(await screen.findByRole("status")).toHaveTextContent("Kata sandi diubah")
    const call = fetchMock.mock.calls[0]
    expect(call[0]).toBe("/api/profile")
    expect((call[1] as RequestInit).method).toBe("PUT")
    expect(JSON.parse((call[1] as RequestInit).body as string)).toEqual({
      currentPassword: "lama12345",
      newPassword: "baru67890",
    })
    await waitFor(() => expect(auth.logout).toHaveBeenCalledWith("/login?notice=password-changed"), { timeout: 4000 })
  })

  it("wrong_current_password_shows_on_field_and_does_not_log_out", async () => {
    const actor = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      errorEnvelope(400, "VALIDATION_ERROR", "Data tidak valid.", [
        { field: "currentPassword", message: "Kata sandi saat ini salah." },
      ]),
    )
    render(<PasswordForm />)

    await fill(actor, "salah12345", "baru67890")

    expect(await screen.findByText("Kata sandi saat ini salah.")).toBeInTheDocument()
    expect(screen.getByLabelText("Kata sandi saat ini")).toHaveAttribute("aria-invalid", "true")
    expect(screen.queryByRole("status")).not.toBeInTheDocument()
    expect(auth.logout).not.toHaveBeenCalled()
  })
})
