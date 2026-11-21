import { useState } from "react"
import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { errorEnvelope, okEnvelope } from "@/features/tasks/test-fixtures"

import { ProfileForm } from "./ProfileForm"
import { makeProfile } from "./test-fixtures"
import type { Profile } from "./types"

const auth = vi.hoisted(() => ({ refreshUser: vi.fn(), logout: vi.fn() }))
vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: () => auth }))

const fetchMock = vi.fn()
const bodyOf = (call: unknown[]) => JSON.parse((call[1] as RequestInit).body as string) as Record<string, unknown>

beforeEach(() => {
  fetchMock.mockReset()
  auth.refreshUser.mockReset().mockResolvedValue(undefined)
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

/** Mirrors SettingsView: the parent swaps in the profile returned by the server. */
function Harness({ onSaved }: { onSaved: (profile: Profile) => void }) {
  const [profile, setProfile] = useState(makeProfile())
  return (
    <ProfileForm
      profile={profile}
      onSaved={(next) => {
        setProfile(next)
        onSaved(next)
      }}
    />
  )
}

async function replaceText(actor: ReturnType<typeof userEvent.setup>, label: string, text: string) {
  const input = screen.getByLabelText(label)
  await actor.clear(input)
  await actor.type(input, text)
}

describe("ProfileForm", () => {
  it("email_change_requires_current_password_field", async () => {
    const actor = userEvent.setup()
    render(<ProfileForm profile={makeProfile()} onSaved={vi.fn()} />)
    expect(screen.queryByLabelText(/Kata sandi saat ini/)).not.toBeInTheDocument()

    await replaceText(actor, "Email", "baru@example.test")
    expect(screen.getByLabelText(/Kata sandi saat ini/)).toBeInTheDocument()

    await actor.click(screen.getByRole("button", { name: "Simpan profil" }))
    expect(await screen.findByText("Kata sandi saat ini wajib diisi untuk mengubah email.")).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()

    await replaceText(actor, "Email", "  BUDI@example.test ")
    expect(screen.queryByLabelText(/Kata sandi saat ini/)).not.toBeInTheDocument() // same email after trim/lowercase
  })

  it("sends_email_and_current_password_when_email_changes_then_refreshes_user", async () => {
    const actor = userEvent.setup()
    const onSaved = vi.fn()
    render(<Harness onSaved={onSaved} />)
    fetchMock.mockResolvedValueOnce(okEnvelope(makeProfile({ email: "baru@example.test" })))

    await replaceText(actor, "Email", "baru@example.test")
    await actor.type(screen.getByLabelText(/Kata sandi saat ini/), "rahasia123")
    await actor.click(screen.getByRole("button", { name: "Simpan profil" }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
    const call = fetchMock.mock.calls[0]
    expect(call[0]).toBe("/api/profile")
    expect((call[1] as RequestInit).method).toBe("PUT")
    expect(bodyOf(call)).toEqual({ fullName: "Budi Contoh", email: "baru@example.test", currentPassword: "rahasia123" })
    expect(auth.refreshUser).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole("status")).toHaveTextContent("Profil disimpan")
    expect(screen.queryByLabelText(/Kata sandi saat ini/)).not.toBeInTheDocument()
  })

  it("name_only_change_sends_no_email_or_password", async () => {
    const actor = userEvent.setup()
    render(<ProfileForm profile={makeProfile()} onSaved={vi.fn()} />)
    fetchMock.mockResolvedValueOnce(okEnvelope(makeProfile({ fullName: "Budi Baru" })))

    await replaceText(actor, "Nama lengkap", "Budi Baru")
    await actor.click(screen.getByRole("button", { name: "Simpan profil" }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    expect(bodyOf(fetchMock.mock.calls[0])).toEqual({ fullName: "Budi Baru" })
  })

  it("wrong_current_password_400_shows_on_field", async () => {
    const actor = userEvent.setup()
    render(<ProfileForm profile={makeProfile()} onSaved={vi.fn()} />)
    fetchMock.mockResolvedValueOnce(
      errorEnvelope(400, "VALIDATION_ERROR", "Data tidak valid.", [
        { field: "currentPassword", message: "Kata sandi saat ini salah." },
      ]),
    )

    await replaceText(actor, "Email", "baru@example.test")
    await actor.type(screen.getByLabelText(/Kata sandi saat ini/), "salah-banget1")
    await actor.click(screen.getByRole("button", { name: "Simpan profil" }))

    expect(await screen.findByText("Kata sandi saat ini salah.")).toBeInTheDocument()
    expect(auth.refreshUser).not.toHaveBeenCalled()
  })

  it("email_taken_409_shows_on_email_field", async () => {
    const actor = userEvent.setup()
    render(<ProfileForm profile={makeProfile()} onSaved={vi.fn()} />)
    fetchMock.mockResolvedValueOnce(errorEnvelope(409, "CONFLICT"))

    await replaceText(actor, "Email", "dipakai@example.test")
    await actor.type(screen.getByLabelText(/Kata sandi saat ini/), "rahasia123")
    await actor.click(screen.getByRole("button", { name: "Simpan profil" }))

    expect(await screen.findByText("Email sudah dipakai akun lain.")).toBeInTheDocument()
  })
})
