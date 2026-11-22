import type { CalendarTask } from "./types"

// Synthetic data only. Shared by unit tests; never imported by app code.
export function makeCalendarTask(overrides: Partial<CalendarTask> = {}): CalendarTask {
  return {
    id: 1,
    title: "Laporan praktikum contoh",
    subject: "Fisika Dasar",
    deadline: "2026-11-21T03:00:00Z",
    priority: "HIGH",
    status: "TODO",
    ...overrides,
  }
}
