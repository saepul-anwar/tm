import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { errorEnvelope, okEnvelope } from "@/features/tasks/test-fixtures"

import { ReminderToggles } from "./ReminderToggles"

const fetchMock = vi.fn()
const ALL_ON = { reminder24Hours: true, reminder6Hours: true, reminder1Hour: true, reminderAtDeadline: true }

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

const sw = (name: string) => screen.findByRole("switch", { name })

describe("ReminderToggles", () => {
  it("ReminderToggles_send_four_booleans", async () => {
    const actor = userEvent.setup()
    fetchMock.mockResolvedValueOnce(okEnvelope(ALL_ON))
    render(<ReminderToggles />)
    const sixHours = await sw("6 jam sebelum deadline")
    expect(sixHours).toBeChecked()

    fetchMock.mockResolvedValueOnce(okEnvelope({ ...ALL_ON, reminder6Hours: false }))
    await actor.click(sixHours)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(url).toBe("/api/reminders/settings")
    expect(init.method).toBe("PUT")
    expect(JSON.parse(init.body as string)).toEqual({
      reminder24Hours: true,
      reminder6Hours: false,
      reminder1Hour: true,
      reminderAtDeadline: true,
    })
    await waitFor(() => expect(sixHours).not.toBeChecked())
  })

  it("optimistically_updates_and_rolls_back_with_error_on_failure", async () => {
    const actor = userEvent.setup()
    fetchMock.mockResolvedValueOnce(okEnvelope(ALL_ON))
    render(<ReminderToggles />)
    const oneHour = await sw("1 jam sebelum deadline")

    let rejectPut: (response: Response) => void = () => {}
    fetchMock.mockReturnValueOnce(new Promise<Response>((resolve) => (rejectPut = resolve)))
    await actor.click(oneHour)
    expect(oneHour).not.toBeChecked() // optimistic, before the server answers

    rejectPut(errorEnvelope(500, "INTERNAL_ERROR"))
    expect(await screen.findByRole("alert")).toHaveTextContent("Terjadi kesalahan di server")
    await waitFor(() => expect(oneHour).toBeChecked())
  })

  it("shows_error_with_retry_when_loading_fails", async () => {
    const actor = userEvent.setup()
    fetchMock.mockResolvedValueOnce(errorEnvelope(500, "INTERNAL_ERROR"))
    render(<ReminderToggles />)
    expect(await screen.findByRole("alert")).toBeInTheDocument()

    fetchMock.mockResolvedValueOnce(okEnvelope({ ...ALL_ON, reminderAtDeadline: false }))
    await actor.click(screen.getByRole("button", { name: "Coba lagi" }))

    expect(await sw("Saat deadline")).not.toBeChecked()
  })
})
