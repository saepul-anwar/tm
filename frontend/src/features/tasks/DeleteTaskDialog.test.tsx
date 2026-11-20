import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { DeleteTaskDialog } from "./DeleteTaskDialog"
import { errorEnvelope, makeTask } from "./test-fixtures"

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe("DeleteTaskDialog", () => {
  it("DeleteTaskDialog_requires_confirmation", async () => {
    const actor = userEvent.setup()
    const onDeleted = vi.fn()
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    render(<DeleteTaskDialog task={makeTask({ id: 8 })} onClose={vi.fn()} onDeleted={onDeleted} />)

    const dialog = await screen.findByRole("dialog", { name: "Hapus tugas?" })
    expect(dialog).toHaveTextContent("Laporan praktikum contoh")
    expect(fetchMock).not.toHaveBeenCalled()

    await actor.click(screen.getByRole("button", { name: "Hapus" }))

    await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1))
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe("/api/tasks/8")
    expect(init.method).toBe("DELETE")
  })

  it("cancel_closes_without_deleting", async () => {
    const actor = userEvent.setup()
    const onClose = vi.fn()
    render(<DeleteTaskDialog task={makeTask()} onClose={onClose} onDeleted={vi.fn()} />)

    await actor.click(await screen.findByRole("button", { name: "Batal" }))

    expect(onClose).toHaveBeenCalled()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("escape_closes_without_deleting", async () => {
    const actor = userEvent.setup()
    const onClose = vi.fn()
    render(<DeleteTaskDialog task={makeTask()} onClose={onClose} onDeleted={vi.fn()} />)

    await screen.findByRole("dialog")
    await actor.keyboard("{Escape}")

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("moves_focus_into_the_dialog_when_opened", async () => {
    render(<DeleteTaskDialog task={makeTask()} onClose={vi.fn()} onDeleted={vi.fn()} />)

    const dialog = await screen.findByRole("dialog")
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true))
  })

  it("shows_an_error_and_stays_open_when_delete_fails", async () => {
    const actor = userEvent.setup()
    const onDeleted = vi.fn()
    fetchMock.mockResolvedValue(errorEnvelope(404, "NOT_FOUND", "Tugas tidak ditemukan."))
    render(<DeleteTaskDialog task={makeTask()} onClose={vi.fn()} onDeleted={onDeleted} />)

    await actor.click(await screen.findByRole("button", { name: "Hapus" }))

    expect(await screen.findByRole("alert")).toHaveTextContent("Data tidak ditemukan")
    expect(onDeleted).not.toHaveBeenCalled()
  })

  it("renders_nothing_without_a_task", () => {
    render(<DeleteTaskDialog task={null} onClose={vi.fn()} onDeleted={vi.fn()} />)
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  })
})
