import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { errorEnvelope, makeTask, okEnvelope } from "@/features/tasks/test-fixtures"

import { DashboardView } from "./DashboardView"
import type { DashboardSummary } from "./types"

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({
    status: "authenticated",
    user: { id: 1, fullName: "Budi Contoh", email: "budi@example.com", timezone: "Asia/Jakarta" },
  }),
}))

const fetchMock = vi.fn()
const callsTo = (path: string) => fetchMock.mock.calls.filter((c) => String(c[0]) === path)

function route(handlers: Record<string, () => Response>) {
  fetchMock.mockImplementation(async (url: string) => {
    const handler = handlers[`GET ${url}`]
    if (!handler) throw new Error(`Unexpected request: ${url}`)
    return handler()
  })
}

const summary = (overrides: Partial<DashboardSummary> = {}): DashboardSummary => ({
  counts: { total: 7, todo: 3, inProgress: 2, completed: 2, overdue: 0, upcoming: 2 },
  completionPercent: 29,
  dueSoon: [makeTask({ id: 11, title: "Tugas segera", deadlineCategory: "DUE_TODAY" })],
  topPriority: [makeTask({ id: 12, title: "Tugas prioritas", priority: "URGENT", deadlineCategory: "UPCOMING" })],
  ...overrides,
})

const section = (name: string) => screen.getByRole("region", { name })

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe("DashboardView", () => {
  it("DashboardPage_shows_skeleton_then_data_then_error_state", async () => {
    const actor = userEvent.setup()
    let failing = false
    route({
      "GET /api/dashboard/summary": () => okEnvelope(summary()),
      "GET /api/dashboard/upcoming": () => okEnvelope([makeTask({ id: 13, title: "Tugas minggu ini", deadlineCategory: "DUE_TOMORROW" })]),
      "GET /api/dashboard/overdue": () => okEnvelope([]),
    })
    const { unmount } = render(<DashboardView />)

    expect(screen.getByTestId("dashboard-skeleton")).toBeInTheDocument()
    expect(await screen.findByRole("list", { name: "Ringkasan tugas" })).toBeInTheDocument()
    expect(screen.queryByTestId("dashboard-skeleton")).not.toBeInTheDocument()
    expect(within(section("Segera diselesaikan")).getByRole("link", { name: "Tugas segera" })).toBeInTheDocument()
    expect(within(section("Prioritas tertinggi")).getByRole("link", { name: "Tugas prioritas" })).toBeInTheDocument()
    expect(within(await screen.findByRole("region", { name: "Deadline 7 hari ke depan" })).getByRole("link", { name: "Tugas minggu ini" })).toBeInTheDocument()
    unmount()

    // Error state with retry.
    fetchMock.mockReset()
    failing = true
    route({
      "GET /api/dashboard/summary": () => (failing ? errorEnvelope(500, "INTERNAL_ERROR") : okEnvelope(summary())),
      "GET /api/dashboard/upcoming": () => okEnvelope([]),
      "GET /api/dashboard/overdue": () => okEnvelope([]),
    })
    render(<DashboardView />)

    const alert = await screen.findByRole("alert")
    expect(alert).toHaveTextContent("Terjadi kesalahan di server")
    expect(screen.queryByRole("list", { name: "Ringkasan tugas" })).not.toBeInTheDocument()

    failing = false
    await actor.click(within(alert).getByRole("button", { name: "Coba lagi" }))
    expect(await screen.findByRole("list", { name: "Ringkasan tugas" })).toBeInTheDocument()
    expect(callsTo("/api/dashboard/summary")).toHaveLength(2)
  })

  it("retry_refetches_only_the_failed_section", async () => {
    const actor = userEvent.setup()
    let failing = true
    route({
      "GET /api/dashboard/summary": () => okEnvelope(summary()),
      "GET /api/dashboard/upcoming": () => (failing ? errorEnvelope(500, "INTERNAL_ERROR") : okEnvelope([makeTask({ id: 13, title: "Pulih" })])),
      "GET /api/dashboard/overdue": () => okEnvelope([]),
    })
    render(<DashboardView />)

    const upcoming = await screen.findByRole("region", { name: "Deadline 7 hari ke depan" })
    const alert = await within(upcoming).findByRole("alert")
    // The rest of the dashboard stays usable.
    expect(screen.getByRole("list", { name: "Ringkasan tugas" })).toBeInTheDocument()

    failing = false
    await actor.click(within(alert).getByRole("button", { name: "Coba lagi" }))
    expect(await within(upcoming).findByRole("link", { name: "Pulih" })).toBeInTheDocument()
    expect(callsTo("/api/dashboard/upcoming")).toHaveLength(2)
    expect(callsTo("/api/dashboard/summary")).toHaveLength(1)
  })

  it("empty_dashboard_shows_empty_state_cta_to_create_task", async () => {
    route({
      "GET /api/dashboard/summary": () =>
        okEnvelope(summary({ counts: { total: 0, todo: 0, inProgress: 0, completed: 0, overdue: 0, upcoming: 0 }, completionPercent: 0, dueSoon: [], topPriority: [] })),
      "GET /api/dashboard/upcoming": () => okEnvelope([]),
      "GET /api/dashboard/overdue": () => okEnvelope([]),
    })
    render(<DashboardView />)

    expect(await screen.findByText("Belum ada tugas")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Buat tugas" })).toHaveAttribute("href", "/tasks/new")
    expect(screen.queryByRole("list", { name: "Ringkasan tugas" })).not.toBeInTheDocument()
  })

  it("overdue_section_hidden_when_empty", async () => {
    route({
      "GET /api/dashboard/summary": () => okEnvelope(summary()),
      "GET /api/dashboard/upcoming": () => okEnvelope([]),
      "GET /api/dashboard/overdue": () => okEnvelope([]),
    })
    render(<DashboardView />)

    await screen.findByRole("list", { name: "Ringkasan tugas" })
    await screen.findByRole("region", { name: "Deadline 7 hari ke depan" })
    expect(screen.queryByRole("region", { name: "Terlambat" })).not.toBeInTheDocument()
  })

  it("overdue_section_shown_with_danger_styling_when_present", async () => {
    route({
      "GET /api/dashboard/summary": () => okEnvelope(summary({ counts: { total: 7, todo: 3, inProgress: 2, completed: 2, overdue: 1, upcoming: 0 } })),
      "GET /api/dashboard/upcoming": () => okEnvelope([]),
      "GET /api/dashboard/overdue": () => okEnvelope([makeTask({ id: 99, title: "Tugas lewat", deadlineCategory: "OVERDUE" })]),
    })
    render(<DashboardView />)

    const overdue = await screen.findByRole("region", { name: "Terlambat" })
    expect(overdue).toHaveAttribute("data-danger", "true")
    expect(within(overdue).getByRole("link", { name: "Tugas lewat" })).toHaveAttribute("href", "/tasks/99")
  })
})
