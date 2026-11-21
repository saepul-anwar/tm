import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { TaskListView } from "./TaskListView"
import { errorEnvelope, makeTask, okEnvelope, pageEnvelope } from "./test-fixtures"

const mocks = vi.hoisted(() => ({ search: "", replace: vi.fn() }))

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({
    status: "authenticated",
    user: { id: 1, fullName: "Budi Contoh", email: "budi@example.com", timezone: "Asia/Jakarta" },
  }),
}))
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace, push: vi.fn() }),
  usePathname: () => "/tasks",
  useSearchParams: () => new URLSearchParams(mocks.search),
}))

const fetchMock = vi.fn()
const urlOf = (call: unknown[]) => String(call[0])
const callsTo = (fragment: string, method = "GET") =>
  fetchMock.mock.calls.filter((c) => urlOf(c).includes(fragment) && ((c[1] as RequestInit)?.method ?? "GET") === method)

/** Routes mocked backend responses by method + path so request order does not matter. */
function route(handlers: Record<string, () => Response>) {
  fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
    const key = `${init?.method ?? "GET"} ${url.split("?")[0]}`
    const handler = handlers[key]
    if (!handler) throw new Error(`Unexpected request: ${key}`)
    return handler()
  })
}

beforeEach(() => {
  mocks.search = ""
  mocks.replace.mockReset()
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe("TaskListView", () => {
  it("TaskList_shows_empty_and_error_states", async () => {
    const actor = userEvent.setup()
    let failing = true
    route({
      "GET /api/tasks": () => (failing ? errorEnvelope(500, "INTERNAL_ERROR") : pageEnvelope([])),
      "GET /api/tasks/subjects": () => okEnvelope([]),
    })
    render(<TaskListView />)

    expect(screen.getByTestId("task-skeleton")).toBeInTheDocument()
    const alert = await screen.findByRole("alert")
    expect(alert).toHaveTextContent("Terjadi kesalahan di server")

    failing = false
    await actor.click(screen.getByRole("button", { name: "Coba lagi" }))

    expect(await screen.findByText("Belum ada tugas")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Buat tugas" })).toHaveAttribute("href", "/tasks/new")
  })

  it("shows_a_filtered_empty_state_with_reset_when_filters_are_active", async () => {
    mocks.search = "status=TODO"
    route({
      "GET /api/tasks": () => pageEnvelope([]),
      "GET /api/tasks/subjects": () => okEnvelope([]),
    })
    render(<TaskListView />)

    expect(await screen.findByText("Tidak ada tugas yang cocok dengan filter.")).toBeInTheDocument()
  })

  it("renders_tasks_with_priority_badge_local_deadline_and_overdue_hint", async () => {
    route({
      "GET /api/tasks": () =>
        pageEnvelope([makeTask({ id: 1, deadline: "2020-01-01T16:59:00Z", priority: "URGENT" })]),
      "GET /api/tasks/subjects": () => okEnvelope(["Fisika Dasar"]),
    })
    render(<TaskListView />)

    const table = await screen.findByRole("table")
    expect(within(table).getByRole("link", { name: "Laporan praktikum contoh" })).toHaveAttribute("href", "/tasks/1")
    expect(within(table).getByText("Mendesak")).toBeInTheDocument()
    expect(within(table).getByText(/1 Jan 2020.*23\.59/)).toBeInTheDocument()
    expect(within(table).getByText(/Terlambat/)).toBeInTheDocument()
  })

  it("requests_the_list_with_plus_offsets_encoded_as_percent_2B", async () => {
    mocks.search = "deadlineFrom=2026-12-01&deadlineTo=2026-12-02"
    route({
      "GET /api/tasks": () => pageEnvelope([]),
      "GET /api/tasks/subjects": () => okEnvelope([]),
    })
    render(<TaskListView />)

    await waitFor(() => expect(callsTo("/api/tasks?")).toHaveLength(1))
    const url = urlOf(callsTo("/api/tasks?")[0])
    expect(url).toContain("deadlineFrom=2026-12-01T00%3A00%3A00%2B07%3A00")
    expect(url).toContain("deadlineTo=2026-12-03T00%3A00%3A00%2B07%3A00")
  })

  it("completed_variant_requests_completed_tasks_sorted_by_completedAt_desc", async () => {
    route({
      "GET /api/tasks": () => pageEnvelope([]),
      "GET /api/tasks/subjects": () => okEnvelope([]),
    })
    render(<TaskListView variant="completed" />)

    await waitFor(() => expect(callsTo("/api/tasks?")).toHaveLength(1))
    const url = urlOf(callsTo("/api/tasks?")[0])
    expect(url).toContain("status=COMPLETED")
    expect(url).toContain("sort=completedAt")
    expect(url).toContain("dir=desc")
    expect(await screen.findByText("Belum ada tugas selesai")).toBeInTheDocument()
  })

  it("status_change_calls_patch_and_updates_list", async () => {
    const actor = userEvent.setup()
    let current = makeTask({ id: 4, status: "TODO" })
    route({
      "GET /api/tasks": () => pageEnvelope([current]),
      "GET /api/tasks/subjects": () => okEnvelope([]),
      "PATCH /api/tasks/4/status": () => {
        current = makeTask({ id: 4, status: "IN_PROGRESS" })
        return okEnvelope(current)
      },
    })
    render(<TaskListView />)

    const table = await screen.findByRole("table")
    const select = within(table).getByLabelText("Status tugas Laporan praktikum contoh")
    expect(select).toHaveValue("TODO")
    await actor.selectOptions(select, "IN_PROGRESS")

    await waitFor(() => expect(callsTo("/api/tasks/4/status", "PATCH")).toHaveLength(1))
    expect(JSON.parse((callsTo("/api/tasks/4/status", "PATCH")[0][1] as RequestInit).body as string)).toEqual({
      status: "IN_PROGRESS",
    })
    await waitFor(() =>
      expect(within(screen.getByRole("table")).getByLabelText("Status tugas Laporan praktikum contoh")).toHaveValue("IN_PROGRESS"),
    )
  })

  it("tandai_selesai_patches_status_to_completed_and_is_hidden_for_completed_tasks", async () => {
    const actor = userEvent.setup()
    let current = makeTask({ id: 4, status: "TODO" })
    route({
      "GET /api/tasks": () => pageEnvelope([current]),
      "GET /api/tasks/subjects": () => okEnvelope([]),
      "PATCH /api/tasks/4/status": () => {
        current = makeTask({ id: 4, status: "COMPLETED", completedAt: "2026-12-01T00:00:00Z" })
        return okEnvelope(current)
      },
    })
    render(<TaskListView />)

    const table = await screen.findByRole("table")
    await actor.click(within(table).getByRole("button", { name: "Tandai selesai: Laporan praktikum contoh" }))

    await waitFor(() =>
      expect(JSON.parse((callsTo("/api/tasks/4/status", "PATCH")[0][1] as RequestInit).body as string)).toEqual({
        status: "COMPLETED",
      }),
    )
    await waitFor(() =>
      expect(within(screen.getByRole("table")).queryByRole("button", { name: /Tandai selesai/ })).not.toBeInTheDocument(),
    )
  })

  it("shows_an_error_and_keeps_the_list_when_the_status_change_fails", async () => {
    const actor = userEvent.setup()
    route({
      "GET /api/tasks": () => pageEnvelope([makeTask({ id: 4 })]),
      "GET /api/tasks/subjects": () => okEnvelope([]),
      "PATCH /api/tasks/4/status": () => errorEnvelope(404, "NOT_FOUND", "Tugas tidak ditemukan."),
    })
    render(<TaskListView />)

    const table = await screen.findByRole("table")
    await actor.selectOptions(within(table).getByLabelText("Status tugas Laporan praktikum contoh"), "COMPLETED")

    expect(await screen.findByRole("alert")).toHaveTextContent("Data tidak ditemukan")
  })

  it("paginates_through_the_url", async () => {
    const actor = userEvent.setup()
    route({
      "GET /api/tasks": () => pageEnvelope([makeTask()], 25, 0, 10),
      "GET /api/tasks/subjects": () => okEnvelope([]),
    })
    render(<TaskListView />)

    await screen.findByRole("table")
    expect(screen.getByText(/Halaman 1 dari 3/)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Sebelumnya" })).toBeDisabled()
    await actor.click(screen.getByRole("button", { name: "Berikutnya" }))
    expect(mocks.replace).toHaveBeenLastCalledWith("/tasks?page=1", { scroll: false })
  })

  it("delete_from_the_list_needs_confirmation_before_calling_delete", async () => {
    const actor = userEvent.setup()
    route({
      "GET /api/tasks": () => pageEnvelope([makeTask({ id: 4 })]),
      "GET /api/tasks/subjects": () => okEnvelope([]),
      "DELETE /api/tasks/4": () => jsonNoContent(),
    })
    render(<TaskListView />)

    const table = await screen.findByRole("table")
    await actor.click(within(table).getByRole("button", { name: "Hapus: Laporan praktikum contoh" }))
    expect(await screen.findByRole("dialog")).toBeInTheDocument()
    expect(callsTo("/api/tasks/4", "DELETE")).toHaveLength(0)

    await actor.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Hapus" }))
    await waitFor(() => expect(callsTo("/api/tasks/4", "DELETE")).toHaveLength(1))
  })
})

function jsonNoContent() {
  return new Response(null, { status: 204 })
}
