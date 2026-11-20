import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { errorEnvelope, okEnvelope } from "@/features/tasks/test-fixtures"

import { SettingsView } from "./SettingsView"
import { makeProfile } from "./test-fixtures"

const auth = vi.hoisted(() => ({ refreshUser: vi.fn(), logout: vi.fn() }))
vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: () => auth }))

const fetchMock = vi.fn()
const ALL_ON = { reminder24Hours: true, reminder6Hours: true, reminder1Hour: true, reminderAtDeadline: true }

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe("SettingsView", () => {
  it("renders_all_sections_from_the_profile", async () => {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(url === "/api/profile" ? okEnvelope(makeProfile()) : okEnvelope(ALL_ON)),
    )
    render(<SettingsView />)

    expect(await screen.findByLabelText("Nama lengkap")).toHaveValue("Budi Contoh")
    for (const name of ["Profil", "Ganti password", "Zona waktu", "Telegram", "Pengingat"]) {
      expect(screen.getByRole("heading", { name })).toBeInTheDocument()
    }
    expect(screen.getByRole("button", { name: "Hubungkan Telegram" })).toBeInTheDocument()
  })

  it("shows_error_with_retry_when_the_profile_fails_to_load", async () => {
    const actor = userEvent.setup()
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(url === "/api/profile" ? errorEnvelope(500, "INTERNAL_ERROR") : okEnvelope(ALL_ON)),
    )
    render(<SettingsView />)
    expect((await screen.findAllByRole("alert")).length).toBeGreaterThan(0)

    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(url === "/api/profile" ? okEnvelope(makeProfile()) : okEnvelope(ALL_ON)),
    )
    await actor.click(screen.getAllByRole("button", { name: "Coba lagi" })[0])

    expect(await screen.findByLabelText("Nama lengkap")).toBeInTheDocument()
  })
})
