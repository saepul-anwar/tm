import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { errorEnvelope, okEnvelope } from "@/features/tasks/test-fixtures"

import { CalendarView } from "./CalendarView"
import { makeCalendarTask } from "./test-fixtures"

const mocks = vi.hoisted(() => {
  const listeners = new Set<() => void>()
  const nav = {
    search: "",
    replace: undefined as unknown as (url: string) => void,
  }
  nav.replace = (url: string) => {
    nav.search = url.includes("?") ? url.split("?")[1] : ""
    listeners.forEach((listener) => listener())
  }
  return { auth: { status: "authenticated", timezone: "Asia/Jakarta" }, nav, listeners }
})

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({
    status: mocks.auth.status,
    user: { id: 1, fullName: "Budi Contoh", email: "budi@example.com", timezone: mocks.auth.timezone },
  }),
}))
vi.mock("next/navigation", async () => {
  const React = await import("react")
  const subscribe = (listener: () => void) => {
    mocks.listeners.add(listener)
    return () => mocks.listeners.delete(listener)
  }
  return {
    useRouter: () => ({ replace: vi.fn((url: string) => mocks.nav.replace(url)), push: vi.fn() }),
    usePathname: () => "/calendar",
    useSearchParams: () => {
      const search = React.useSyncExternalStore(subscribe, () => mocks.nav.search, () => mocks.nav.search)
      return React.useMemo(() => new URLSearchParams(search), [search])
    },
  }
})

const fetchMock = vi.fn()
const requested = () => fetchMock.mock.calls.map((call) => String(call[0]))
const lastRequest = () => requested().at(-1)

// 2026-11-21 12:00 in Jakarta.
const NOW = "2026-11-21T05:00:00Z"

const range = (from: string, to: string) => `/api/calendar/tasks?from=${from}&to=${to}`

function respondWith(tasks: ReturnType<typeof makeCalendarTask>[]) {
  fetchMock.mockImplementation(async () => okEnvelope(tasks))
}

const grid = () => screen.getByRole("table", { name: "Kalender bulanan" })

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] })
  vi.setSystemTime(new Date(NOW))
  mocks.auth.status = "authenticated"
  mocks.auth.timezone = "Asia/Jakarta"
  mocks.nav.search = "view=month&date=2026-11-21"
  fetchMock.mockReset()
  respondWith([])
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe("CalendarView", () => {
  it("month_week_day_switch_requests_matching_range_in_profile_timezone", async () => {
    const actor = userEvent.setup()
    render(<CalendarView />)

    await screen.findByRole("table", { name: "Kalender bulanan" })
    expect(requested()).toEqual([range("2026-10-26T00%3A00%3A00%2B07%3A00", "2026-12-07T00%3A00%3A00%2B07%3A00")])

    await actor.click(screen.getByRole("button", { name: "Minggu" }))
    expect(lastRequest()).toBe(range("2026-11-16T00%3A00%3A00%2B07%3A00", "2026-11-23T00%3A00%3A00%2B07%3A00"))

    await actor.click(screen.getByRole("button", { name: "Hari" }))
    expect(lastRequest()).toBe(range("2026-11-21T00%3A00%3A00%2B07%3A00", "2026-11-22T00%3A00%3A00%2B07%3A00"))
    expect(mocks.nav.search).toBe("view=day&date=2026-11-21")
  })

  it("range_uses_the_profile_timezone_not_the_browser_and_is_dst_safe", async () => {
    mocks.auth.timezone = "America/New_York"
    render(<CalendarView />)

    await screen.findByRole("table", { name: "Kalender bulanan" })
    // DST ends 2026-11-01: grid starts in EDT (-04:00) and ends in EST (-05:00).
    expect(requested()).toEqual([range("2026-10-26T00%3A00%3A00-04%3A00", "2026-12-07T00%3A00%3A00-05%3A00")])
  })

  it("next_prev_navigation_refetches", async () => {
    const actor = userEvent.setup()
    render(<CalendarView />)
    await screen.findByRole("table", { name: "Kalender bulanan" })
    expect(requested()).toHaveLength(1)
    expect(screen.getByRole("heading", { name: "November 2026" })).toBeInTheDocument()

    await actor.click(screen.getByRole("button", { name: "Bulan berikutnya" }))
    expect(screen.getByRole("heading", { name: "Desember 2026" })).toBeInTheDocument()
    expect(mocks.nav.search).toBe("view=month&date=2026-12-21")
    expect(requested()).toHaveLength(2)
    expect(lastRequest()).toBe(range("2026-11-30T00%3A00%3A00%2B07%3A00", "2027-01-04T00%3A00%3A00%2B07%3A00"))

    await actor.click(screen.getByRole("button", { name: "Bulan sebelumnya" }))
    await actor.click(screen.getByRole("button", { name: "Bulan sebelumnya" }))
    expect(screen.getByRole("heading", { name: "Oktober 2026" })).toBeInTheDocument()
    expect(requested()).toHaveLength(4)
    expect(lastRequest()).toBe(range("2026-09-28T00%3A00%3A00%2B07%3A00", "2026-11-02T00%3A00%3A00%2B07%3A00"))
  })

  it("week_and_day_navigation_move_by_their_own_unit", async () => {
    const actor = userEvent.setup()
    mocks.nav.search = "view=week&date=2026-11-21"
    render(<CalendarView />)
    await screen.findByRole("heading", { name: "16–22 November 2026" })

    await actor.click(screen.getByRole("button", { name: "Minggu berikutnya" }))
    expect(mocks.nav.search).toBe("view=week&date=2026-11-28")
    await actor.click(screen.getByRole("button", { name: "Hari" }))
    await actor.click(screen.getByRole("button", { name: "Hari sebelumnya" }))
    expect(mocks.nav.search).toBe("view=day&date=2026-11-27")
  })

  it("hari_ini_jumps_to_today_in_the_profile_timezone", async () => {
    const actor = userEvent.setup()
    // 2026-11-21 20:00 UTC is already 22 Nov in Jakarta but still 21 Nov in New York.
    vi.setSystemTime(new Date("2026-11-21T20:00:00Z"))
    mocks.nav.search = "view=month&date=2026-03-04"
    const { unmount } = render(<CalendarView />)

    await actor.click(screen.getByRole("button", { name: "Hari ini" }))
    expect(mocks.nav.search).toBe("view=month&date=2026-11-22")
    expect(screen.getByRole("heading", { name: "November 2026" })).toBeInTheDocument()
    unmount()

    mocks.auth.timezone = "America/New_York"
    mocks.nav.search = "view=month&date=2026-03-04"
    render(<CalendarView />)
    await actor.click(screen.getByRole("button", { name: "Hari ini" }))
    expect(mocks.nav.search).toBe("view=month&date=2026-11-21")
  })

  it("url_params_restore_view_and_date_and_bad_values_fall_back", async () => {
    mocks.nav.search = "view=week&date=2026-03-04"
    const { unmount } = render(<CalendarView />)
    expect(await screen.findByRole("heading", { name: "2–8 Maret 2026" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Minggu" })).toHaveAttribute("aria-pressed", "true")
    expect(lastRequest()).toBe(range("2026-03-02T00%3A00%3A00%2B07%3A00", "2026-03-09T00%3A00%3A00%2B07%3A00"))
    unmount()

    mocks.nav.search = "view=tahun&date=2026-02-31"
    render(<CalendarView />)
    expect(await screen.findByRole("heading", { name: "November 2026" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Bulan" })).toHaveAttribute("aria-pressed", "true")
  })

  it("month_grid_is_monday_first_marks_today_and_dims_other_months", async () => {
    render(<CalendarView />)
    await screen.findByRole("table", { name: "Kalender bulanan" })

    const headers = within(grid()).getAllByRole("columnheader").map((header) => header.textContent)
    expect(headers).toEqual(["Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"])
    const first = within(grid()).getAllByRole("button", { name: /tugas$/ })[0]
    expect(first).toHaveAccessibleName("26 Oktober 2026, tidak ada tugas")

    const today = within(grid()).getByRole("button", { name: "21 November 2026, tidak ada tugas" })
    expect(today).toHaveAttribute("aria-current", "date")
    expect(within(grid()).getAllByRole("button", { current: "date" })).toHaveLength(1)
    expect(first.closest("td")).toHaveAttribute("data-outside-month", "true")
    expect(today.closest("td")).not.toHaveAttribute("data-outside-month")
  })

  it("clicking_date_lists_tasks_of_that_day", async () => {
    const actor = userEvent.setup()
    respondWith([
      makeCalendarTask({ id: 1, title: "Tugas Sabtu", deadline: "2026-11-21T03:00:00Z" }),
      makeCalendarTask({ id: 2, title: "Tugas Minggu pagi", deadline: "2026-11-22T01:00:00Z", subject: "Kimia" }),
      makeCalendarTask({ id: 3, title: "Tugas Minggu siang", deadline: "2026-11-22T05:30:00Z" }),
    ])
    render(<CalendarView />)

    const panel = await screen.findByRole("region", { name: "Tugas 21 November 2026" })
    expect(within(panel).getByText("Tugas Sabtu")).toBeInTheDocument()

    await actor.click(within(grid()).getByRole("button", { name: "22 November 2026, 2 tugas" }))
    const next = screen.getByRole("region", { name: "Tugas 22 November 2026" })
    const rows = within(next).getAllByRole("listitem")
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent("Tugas Minggu pagi")
    expect(rows[0]).toHaveTextContent("08.00")
    expect(rows[1]).toHaveTextContent("Tugas Minggu siang")
    expect(within(next).queryByText("Tugas Sabtu")).not.toBeInTheDocument()
    expect(mocks.nav.search).toBe("view=month&date=2026-11-22")

    await actor.click(within(grid()).getByRole("button", { name: "23 November 2026, tidak ada tugas" }))
    expect(screen.getByRole("region", { name: "Tugas 23 November 2026" })).toHaveTextContent("Tidak ada tugas pada tanggal ini.")
  })

  it("clicking_task_opens_drawer_with_detail", async () => {
    const actor = userEvent.setup()
    respondWith([
      makeCalendarTask({
        id: 42,
        title: "Makalah Sejarah",
        subject: "Sejarah Indonesia",
        deadline: "2026-11-21T03:00:00Z",
        priority: "URGENT",
        status: "IN_PROGRESS",
      }),
    ])
    render(<CalendarView />)
    await screen.findByRole("region", { name: "Tugas 21 November 2026" })

    await actor.click(within(grid()).getByRole("button", { name: /^Makalah Sejarah/ }))
    const dialog = await screen.findByRole("dialog", { name: "Makalah Sejarah" })
    expect(within(dialog).getByText("Sejarah Indonesia")).toBeInTheDocument()
    expect(within(dialog).getByText(/21 Nov 2026, 10\.00/)).toBeInTheDocument()
    expect(within(dialog).getByText(/Asia\/Jakarta/)).toBeInTheDocument()
    expect(within(dialog).getByText("Mendesak")).toBeInTheDocument()
    expect(within(dialog).getByText("Sedang dikerjakan")).toBeInTheDocument()
    expect(within(dialog).getByRole("link", { name: "Buka detail" })).toHaveAttribute("href", "/tasks/42")
    expect(within(dialog).getByRole("link", { name: "Ubah" })).toHaveAttribute("href", "/tasks/42/edit")

    await actor.keyboard("{Escape}")
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()

    // A row in the day panel opens the same drawer.
    const panel = screen.getByRole("region", { name: "Tugas 21 November 2026" })
    await actor.click(within(panel).getByRole("button", { name: /Makalah Sejarah/ }))
    expect(await screen.findByRole("dialog", { name: "Makalah Sejarah" })).toBeInTheDocument()
  })

  it("priority_colors_are_distinct_and_have_legend", async () => {
    respondWith([
      makeCalendarTask({ id: 1, title: "P rendah", priority: "LOW", deadline: "2026-11-21T01:00:00Z" }),
      makeCalendarTask({ id: 2, title: "P sedang", priority: "MEDIUM", deadline: "2026-11-21T02:00:00Z" }),
      makeCalendarTask({ id: 3, title: "P tinggi", priority: "HIGH", deadline: "2026-11-21T03:00:00Z" }),
    ])
    render(<CalendarView />)
    await screen.findByRole("region", { name: "Tugas 21 November 2026" })

    const legend = screen.getByRole("list", { name: "Legenda prioritas" })
    const items = within(legend).getAllByRole("listitem")
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining("Rendah"),
      expect.stringContaining("Sedang"),
      expect.stringContaining("Tinggi"),
      expect.stringContaining("Mendesak"),
    ])
    expect(new Set(items.map((item) => item.querySelector("[data-legend-swatch]")?.className)).size).toBe(4)

    const chips = within(grid()).getAllByRole("button", { name: /^P (rendah|sedang|tinggi)/ })
    expect(chips.map((chip) => chip.getAttribute("data-priority"))).toEqual(["LOW", "MEDIUM", "HIGH"])
    expect(new Set(chips.map((chip) => chip.className)).size).toBe(3)
    // Priority is also exposed in text, not only color.
    expect(chips[0]).toHaveAccessibleName(/Rendah/)
  })

  it("tasks_near_midnight_land_on_the_right_local_day", async () => {
    respondWith([
      makeCalendarTask({ id: 1, title: "Jam 23.30 WIB", deadline: "2026-11-21T16:30:00Z" }),
      makeCalendarTask({ id: 2, title: "Jam 00.30 WIB", deadline: "2026-11-21T17:30:00Z" }),
    ])
    const { unmount } = render(<CalendarView />)
    await screen.findByRole("button", { name: "21 November 2026, 1 tugas" })
    expect(within(grid()).getByRole("button", { name: "22 November 2026, 1 tugas" })).toBeInTheDocument()
    unmount()

    // The same instants are both on 21 Nov in New York (11:30 and 12:30).
    mocks.auth.timezone = "America/New_York"
    render(<CalendarView />)
    expect(await screen.findByRole("button", { name: "21 November 2026, 2 tugas" })).toBeInTheDocument()
    expect(within(grid()).getByRole("button", { name: "22 November 2026, tidak ada tugas" })).toBeInTheDocument()
  })

  it("more_than_three_tasks_shows_plus_n_lagi", async () => {
    respondWith(
      Array.from({ length: 5 }, (_, index) =>
        makeCalendarTask({ id: index + 1, title: `Tugas ${index + 1}`, deadline: `2026-11-21T0${index}:00:00Z` }),
      ),
    )
    render(<CalendarView />)
    await screen.findByRole("button", { name: "21 November 2026, 5 tugas" })

    const cell = within(grid()).getByRole("button", { name: "21 November 2026, 5 tugas" }).closest("td")!
    expect(within(cell).getAllByRole("button", { name: /^Tugas \d/ })).toHaveLength(3)
    expect(within(cell).getByRole("button", { name: "+2 lagi" })).toBeInTheDocument()
  })

  it("completed_tasks_are_muted_with_line_through", async () => {
    respondWith([
      makeCalendarTask({ id: 1, title: "Sudah selesai", status: "COMPLETED" }),
      makeCalendarTask({ id: 2, title: "Belum selesai", status: "TODO", deadline: "2026-11-21T04:00:00Z" }),
    ])
    render(<CalendarView />)
    await screen.findByRole("region", { name: "Tugas 21 November 2026" })

    const done = within(grid()).getByRole("button", { name: /^Sudah selesai/ })
    const todo = within(grid()).getByRole("button", { name: /^Belum selesai/ })
    expect(done).toHaveAttribute("data-status", "COMPLETED")
    expect(done.querySelector(".line-through")).not.toBeNull()
    expect(todo.querySelector(".line-through")).toBeNull()
  })

  it("week_view_lists_each_day_tasks_in_time_order_and_day_view_shows_agenda", async () => {
    const actor = userEvent.setup()
    respondWith([
      makeCalendarTask({ id: 2, title: "Siang", deadline: "2026-11-20T06:00:00Z" }),
      makeCalendarTask({ id: 1, title: "Pagi", deadline: "2026-11-20T01:00:00Z" }),
      makeCalendarTask({ id: 3, title: "Hari lain", deadline: "2026-11-18T01:00:00Z" }),
    ])
    mocks.nav.search = "view=week&date=2026-11-21"
    render(<CalendarView />)

    const friday = await screen.findByRole("region", { name: "Tugas 20 November 2026" })
    const rows = within(friday).getAllByRole("listitem")
    expect(rows[0]).toHaveTextContent("Pagi")
    expect(rows[1]).toHaveTextContent("Siang")
    expect(screen.getAllByRole("region", { name: /^Tugas \d+ November 2026$/ })).toHaveLength(7)
    expect(screen.getByRole("button", { name: "21 November 2026, tidak ada tugas" })).toHaveAttribute("aria-current", "date")

    await actor.click(screen.getByRole("button", { name: "20 November 2026, 2 tugas" }))
    await actor.click(screen.getByRole("button", { name: "Hari" }))
    const agenda = await screen.findByRole("region", { name: "Tugas 20 November 2026" })
    expect(within(agenda).getAllByRole("listitem")).toHaveLength(2)
    expect(screen.getByRole("heading", { name: "Jumat, 20 November 2026" })).toBeInTheDocument()
  })

  it("shows_skeleton_while_loading_then_error_with_retry", async () => {
    const actor = userEvent.setup()
    let failing = true
    fetchMock.mockImplementation(async () => (failing ? errorEnvelope(500, "INTERNAL_ERROR") : okEnvelope([])))
    render(<CalendarView />)

    expect(screen.getByRole("status", { name: "Memuat kalender" })).toBeInTheDocument()
    const alert = await screen.findByRole("alert")
    expect(alert).toHaveTextContent("Terjadi kesalahan di server")
    expect(screen.queryByRole("table")).not.toBeInTheDocument()
    // Navigation stays usable while the range failed.
    expect(screen.getByRole("button", { name: "Bulan berikutnya" })).toBeEnabled()

    failing = false
    await actor.click(within(alert).getByRole("button", { name: "Coba lagi" }))
    expect(await screen.findByRole("table", { name: "Kalender bulanan" })).toBeInTheDocument()
    expect(requested()).toHaveLength(2)
  })

  it("unauthenticated_users_never_fetch", async () => {
    mocks.auth.status = "anonymous"
    render(<CalendarView />)

    expect(screen.getByRole("status", { name: "Memuat kalender" })).toBeInTheDocument()
    await Promise.resolve()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
