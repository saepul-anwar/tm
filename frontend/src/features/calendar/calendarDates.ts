import { startOfZonedDayIso } from "@/features/tasks/datetime"

// Calendar days are plain "YYYY-MM-DD" strings (a date on the user's wall calendar, no zone).
// Arithmetic runs on UTC midnights purely as a day counter; the zone only matters when a
// local midnight is turned into an instant (see visibleRange), which the datetime helpers do DST-safely.

export type CalendarViewMode = "month" | "week" | "day"
export const VIEW_MODES: readonly CalendarViewMode[] = ["month", "week", "day"]
export const DEFAULT_VIEW: CalendarViewMode = "month"

const DAYS_IN_WEEK = 7
const MONTH_NAMES = [
  "Januari", "Februari", "Maret", "April", "Mei", "Juni",
  "Juli", "Agustus", "September", "Oktober", "November", "Desember",
]
const MONTH_SHORT = ["Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"]
// Monday first, as is common in Indonesia.
export const WEEKDAY_NAMES = ["Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"]
export const WEEKDAY_SHORT = ["Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"]

const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/
const pad = (value: number) => String(value).padStart(2, "0")

type Ymd = { year: number; month: number; day: number }

function toUtc({ year, month, day }: Ymd): Date {
  return new Date(Date.UTC(year, month - 1, day))
}

function format(date: Date): string {
  return `${String(date.getUTCFullYear()).padStart(4, "0")}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

export function isValidDate(value: string | null | undefined): value is string {
  const match = DATE_PATTERN.exec(value ?? "")
  if (!match) return false
  const [year, month, day] = match.slice(1).map(Number)
  const date = toUtc({ year, month, day })
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

function parse(date: string): Ymd {
  const [year, month, day] = date.split("-").map(Number)
  return { year, month, day }
}

export function parseView(value: string | null | undefined): CalendarViewMode {
  return VIEW_MODES.find((mode) => mode === value) ?? DEFAULT_VIEW
}

export function addDays(date: string, amount: number): string {
  const { year, month, day } = parse(date)
  return format(new Date(Date.UTC(year, month - 1, day + amount)))
}

/** Same day of month in another month, clamped to that month's last day (31 Jan + 1 month = 28 Feb). */
export function addMonths(date: string, amount: number): string {
  const { year, month, day } = parse(date)
  const target = new Date(Date.UTC(year, month - 1 + amount, 1))
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate()
  return format(new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth(), Math.min(day, lastDay))))
}

/** 0 = Monday ... 6 = Sunday. */
export function dayOfWeekMondayFirst(date: string): number {
  return (toUtc(parse(date)).getUTCDay() + 6) % DAYS_IN_WEEK
}

export function startOfWeek(date: string): string {
  return addDays(date, -dayOfWeekMondayFirst(date))
}

export function weekDays(date: string): string[] {
  const start = startOfWeek(date)
  return Array.from({ length: DAYS_IN_WEEK }, (_, offset) => addDays(start, offset))
}

/** Whole Monday-first weeks covering the month of `date`, including leading/trailing days. */
export function buildMonthGrid(date: string): { days: string[]; month: string } {
  const { year, month } = parse(date)
  const first = format(new Date(Date.UTC(year, month - 1, 1)))
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const lead = dayOfWeekMondayFirst(first)
  const weeks = Math.ceil((lead + daysInMonth) / DAYS_IN_WEEK)
  const start = addDays(first, -lead)
  return {
    month: first.slice(0, 7),
    days: Array.from({ length: weeks * DAYS_IN_WEEK }, (_, offset) => addDays(start, offset)),
  }
}

export function daysOfView(view: CalendarViewMode, date: string): string[] {
  if (view === "month") return buildMonthGrid(date).days
  if (view === "week") return weekDays(date)
  return [date]
}

export type VisibleRange = { from: string; to: string; days: string[] }

/**
 * The visible days plus the request range [from, to): local midnights in the user's zone as ISO
 * strings with offset. Always at most 42 days (+1 h across DST), well inside the backend's 62-day limit.
 */
export function visibleRange(view: CalendarViewMode, date: string, timeZone: string): VisibleRange {
  const days = daysOfView(view, date)
  return {
    days,
    from: startOfZonedDayIso(days[0], timeZone),
    to: startOfZonedDayIso(days[days.length - 1], timeZone, 1),
  }
}

export function shiftAnchor(view: CalendarViewMode, date: string, direction: 1 | -1): string {
  if (view === "month") return addMonths(date, direction)
  return addDays(date, view === "week" ? direction * DAYS_IN_WEEK : direction)
}

export const monthKey = (date: string) => date.slice(0, 7)

/** "21 November 2026". */
export function longDate(date: string): string {
  const { year, month, day } = parse(date)
  return `${day} ${MONTH_NAMES[month - 1]} ${year}`
}

export function weekdayName(date: string): string {
  return WEEKDAY_NAMES[dayOfWeekMondayFirst(date)]
}

export function dayOfMonth(date: string): number {
  return parse(date).day
}

export function titleFor(view: CalendarViewMode, date: string): string {
  if (view === "month") {
    const { year, month } = parse(date)
    return `${MONTH_NAMES[month - 1]} ${year}`
  }
  if (view === "day") return `${weekdayName(date)}, ${longDate(date)}`

  const days = weekDays(date)
  const start = parse(days[0])
  const end = parse(days[6])
  if (start.year !== end.year) {
    return `${start.day} ${MONTH_SHORT[start.month - 1]} ${start.year} – ${end.day} ${MONTH_SHORT[end.month - 1]} ${end.year}`
  }
  if (start.month !== end.month) {
    return `${start.day} ${MONTH_SHORT[start.month - 1]} – ${end.day} ${MONTH_SHORT[end.month - 1]} ${end.year}`
  }
  return `${start.day}–${end.day} ${MONTH_NAMES[end.month - 1]} ${end.year}`
}
