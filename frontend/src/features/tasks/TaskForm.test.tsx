import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { TaskForm } from "./TaskForm"
import { errorEnvelope, makeTask, okEnvelope } from "./test-fixtures"

const mocks = vi.hoisted(() => ({ timezone: "Asia/Jakarta", push: vi.fn() }))

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({
    status: "authenticated",
    user: { id: 1, fullName: "Budi Contoh", email: "budi@example.com", timezone: mocks.timezone },
  }),
}))
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: mocks.push, replace: vi.fn() }) }))

const fetchMock = vi.fn()

beforeEach(() => {
  mocks.timezone = "Asia/Jakarta"
  mocks.push.mockReset()
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

async function fillRequired(actor: ReturnType<typeof userEvent.setup>) {
  await actor.type(screen.getByLabelText("Judul"), "Laporan praktikum")
  fireEvent.change(screen.getByLabelText("Tanggal deadline"), { target: { value: "2026-12-01" } })
  fireEvent.change(screen.getByLabelText("Jam deadline"), { target: { value: "23:59" } })
}

const lastRequest = () => {
  const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit]
  return { url, method: init.method, body: JSON.parse(init.body as string) }
}

describe("TaskForm", () => {
  it("TaskForm_submits_iso_deadline_in_profile_timezone", async () => {
    const actor = userEvent.setup()
    fetchMock.mockResolvedValue(okEnvelope(makeTask({ id: 9 }), 201))
    render(<TaskForm />)

    await fillRequired(actor)
    await actor.click(screen.getByRole("button", { name: "Simpan tugas" }))

    await waitFor(() => expect(mocks.push).toHaveBeenCalledWith("/tasks"))
    const request = lastRequest()
    expect(request.url).toBe("/api/tasks")
    expect(request.method).toBe("POST")
    expect(request.body.deadline).toBe("2026-12-01T23:59:00+07:00")
    expect(request.body).not.toHaveProperty("version")
  })

  it("TaskForm_uses_the_profile_zone_not_the_browser_zone_for_another_zone", async () => {
    const actor = userEvent.setup()
    mocks.timezone = "America/New_York"
    fetchMock.mockResolvedValue(okEnvelope(makeTask({ id: 9 }), 201))
    render(<TaskForm />)

    await fillRequired(actor)
    await actor.click(screen.getByRole("button", { name: "Simpan tugas" }))

    await waitFor(() => expect(mocks.push).toHaveBeenCalled())
    expect(lastRequest().body.deadline).toBe("2026-12-01T23:59:00-05:00")
  })

  it("shows_required_errors_and_does_not_call_the_api", async () => {
    const actor = userEvent.setup()
    render(<TaskForm />)

    await actor.click(screen.getByRole("button", { name: "Simpan tugas" }))

    expect(await screen.findByText("Judul wajib diisi.")).toBeInTheDocument()
    expect(screen.getByText("Tanggal deadline wajib diisi.")).toBeInTheDocument()
    expect(screen.getByText("Jam deadline wajib diisi.")).toBeInTheDocument()
    expect(screen.getByLabelText("Judul")).toHaveAttribute("aria-invalid", "true")
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("maps_backend_details_to_fields", async () => {
    const actor = userEvent.setup()
    fetchMock.mockResolvedValue(
      errorEnvelope(400, "VALIDATION_ERROR", "Data tidak valid.", [{ field: "title", message: "Judul dari server." }]),
    )
    render(<TaskForm />)

    await fillRequired(actor)
    await actor.click(screen.getByRole("button", { name: "Simpan tugas" }))

    expect(await screen.findByText("Judul dari server.")).toBeInTheDocument()
    expect(mocks.push).not.toHaveBeenCalled()
  })

  it("TaskForm_edit_prefills_values", () => {
    render(<TaskForm task={makeTask({ priority: "URGENT", status: "IN_PROGRESS", subject: "Kimia", description: "Isi" })} />)

    expect(screen.getByLabelText("Judul")).toHaveValue("Laporan praktikum contoh")
    expect(screen.getByLabelText("Deskripsi")).toHaveValue("Isi")
    expect(screen.getByLabelText("Mata kuliah")).toHaveValue("Kimia")
    // 2026-12-01T16:59Z is 23:59 on the same day in Asia/Jakarta.
    expect(screen.getByLabelText("Tanggal deadline")).toHaveValue("2026-12-01")
    expect(screen.getByLabelText("Jam deadline")).toHaveValue("23:59")
    expect(screen.getByLabelText("Prioritas")).toHaveValue("URGENT")
    expect(screen.getByLabelText("Status")).toHaveValue("IN_PROGRESS")
  })

  it("edit_sends_put_with_version_and_navigates_to_detail", async () => {
    const actor = userEvent.setup()
    fetchMock.mockResolvedValue(okEnvelope(makeTask()))
    render(<TaskForm task={makeTask({ id: 5, version: 3 })} />)

    await actor.click(screen.getByRole("button", { name: "Simpan perubahan" }))

    await waitFor(() => expect(mocks.push).toHaveBeenCalledWith("/tasks/5"))
    const request = lastRequest()
    expect(request.url).toBe("/api/tasks/5")
    expect(request.method).toBe("PUT")
    expect(request.body).toMatchObject({ version: 3, deadline: "2026-12-01T23:59:00+07:00" })
  })

  it("handles_409_conflict_with_a_message_and_reload_option", async () => {
    const actor = userEvent.setup()
    const onReload = vi.fn()
    fetchMock.mockResolvedValue(errorEnvelope(409, "CONFLICT", "Versi usang."))
    render(<TaskForm task={makeTask()} onReload={onReload} />)

    await actor.click(screen.getByRole("button", { name: "Simpan perubahan" }))

    const alert = await screen.findByRole("alert")
    expect(alert).toHaveTextContent("Tugas ini sudah diubah di tempat lain")
    expect(mocks.push).not.toHaveBeenCalled()
    await actor.click(screen.getByRole("button", { name: "Muat ulang data" }))
    expect(onReload).toHaveBeenCalledTimes(1)
  })
})
