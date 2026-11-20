import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { okEnvelope } from "@/features/tasks/test-fixtures"

import { TimezoneSelect } from "./TimezoneSelect"
import { makeProfile } from "./test-fixtures"

const auth = vi.hoisted(() => ({ refreshUser: vi.fn(), logout: vi.fn() }))
vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: () => auth }))

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  auth.refreshUser.mockReset().mockResolvedValue(undefined)
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe("TimezoneSelect", () => {
  it("lists_asia_jakarta_first_and_selects_the_current_zone", () => {
    render(<TimezoneSelect timezone="Asia/Jakarta" onSaved={vi.fn()} />)
    const options = screen.getAllByRole("option")
    expect(options[0]).toHaveValue("Asia/Jakarta")
    expect(options.length).toBeGreaterThan(50)
    expect(screen.getByLabelText("Zona waktu")).toHaveValue("Asia/Jakarta")
  })

  it("includes_a_saved_zone_missing_from_the_browser_list", () => {
    render(<TimezoneSelect timezone="Etc/Synthetic" onSaved={vi.fn()} />)
    expect(screen.getByLabelText("Zona waktu")).toHaveValue("Etc/Synthetic")
  })

  it("timezone_save_calls_put_with_only_timezone", async () => {
    const actor = userEvent.setup()
    const onSaved = vi.fn()
    fetchMock.mockResolvedValueOnce(okEnvelope(makeProfile({ timezone: "America/New_York" })))
    render(<TimezoneSelect timezone="Asia/Jakarta" onSaved={onSaved} />)

    await actor.selectOptions(screen.getByLabelText("Zona waktu"), "America/New_York")
    await actor.click(screen.getByRole("button", { name: "Simpan zona waktu" }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
    const call = fetchMock.mock.calls[0]
    expect(call[0]).toBe("/api/profile")
    expect((call[1] as RequestInit).method).toBe("PUT")
    expect(JSON.parse((call[1] as RequestInit).body as string)).toEqual({ timezone: "America/New_York" })
    expect(auth.refreshUser).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole("status")).toHaveTextContent("Zona waktu disimpan")
  })
})
