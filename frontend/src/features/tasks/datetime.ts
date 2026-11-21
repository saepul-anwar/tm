import type { TaskStatus } from "./types"

/** Same default as the backend (users.timezone). The browser zone is never used. */
export const DEFAULT_TIME_ZONE = "Asia/Jakarta"

const MINUTE_MS = 60_000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS

export function resolveTimeZone(timeZone: string | null | undefined): string {
  if (!timeZone) return DEFAULT_TIME_ZONE
  try {
    new Intl.DateTimeFormat("en-US", { timeZone })
    return timeZone
  } catch {
    return DEFAULT_TIME_ZONE
  }
}

const formatters = new Map<string, Intl.DateTimeFormat>()

function partsFormatter(timeZone: string): Intl.DateTimeFormat {
  let formatter = formatters.get(timeZone)
  if (!formatter) {
    formatter = new Intl.DateTimeFormat("en-US", {
      timeZone,
      hourCycle: "h23",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    })
    formatters.set(timeZone, formatter)
  }
  return formatter
}

type Wall = { year: number; month: number; day: number; hour: number; minute: number; second: number }

function wallClock(epochMs: number, timeZone: string): Wall {
  const values: Record<string, number> = {}
  for (const part of partsFormatter(timeZone).formatToParts(new Date(epochMs))) {
    if (part.type !== "literal") values[part.type] = Number(part.value)
  }
  return {
    year: values.year,
    month: values.month,
    day: values.day,
    hour: values.hour,
    minute: values.minute,
    second: values.second,
  }
}

/** Offset (zone minus UTC) in milliseconds at the given instant. */
function offsetMs(epochMs: number, timeZone: string): number {
  const w = wallClock(epochMs, timeZone)
  const asUtc = Date.UTC(w.year, w.month - 1, w.day, w.hour, w.minute, w.second)
  return asUtc - Math.floor(epochMs / 1000) * 1000
}

const pad = (value: number, length = 2) => String(value).padStart(length, "0")

function formatOffset(ms: number): string {
  const totalMinutes = Math.round(ms / MINUTE_MS)
  const sign = totalMinutes < 0 ? "-" : "+"
  const abs = Math.abs(totalMinutes)
  return `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
}

function formatWall(w: Wall, offset: string): string {
  return `${pad(w.year, 4)}-${pad(w.month)}-${pad(w.day)}T${pad(w.hour)}:${pad(w.minute)}:${pad(w.second)}${offset}`
}

/**
 * Resolves a wall-clock time in `timeZone` to an epoch instant (DST-safe). Mirrors the
 * "compatible" rule of Temporal: an ambiguous time (clocks go back) takes the earlier
 * instant; a nonexistent time (clocks go forward) is shifted forward by the gap.
 */
function zonedToEpoch(date: string, time: string, timeZone: string): number {
  const [year, month, day] = date.split("-").map(Number)
  const [hour, minute] = time.split(":").map(Number)
  const localAsUtc = Date.UTC(year, month - 1, day, hour, minute)
  const before = offsetMs(localAsUtc - DAY_MS, timeZone)
  const after = offsetMs(localAsUtc + DAY_MS, timeZone)
  const candidates = [localAsUtc - before, localAsUtc - after].filter(
    (epoch) => offsetMs(epoch, timeZone) === localAsUtc - epoch,
  )
  return candidates.length > 0 ? Math.min(...candidates) : localAsUtc - before
}

/**
 * Combines a date (YYYY-MM-DD) and time (HH:mm) read in `timeZone` into an ISO-8601
 * string with the zone's UTC offset, e.g. 2026-12-01T23:59:00+07:00. A time that does
 * not exist (DST gap) is normalised to the real instant that follows it.
 */
export function zonedDateTimeToIso(date: string, time: string, timeZone: string): string {
  const epoch = zonedToEpoch(date, time, timeZone)
  return formatWall(wallClock(epoch, timeZone), formatOffset(offsetMs(epoch, timeZone)))
}

/** Local midnight of `date` (plus `addDays` calendar days) in `timeZone`, ISO-8601 with offset. */
export function startOfZonedDayIso(date: string, timeZone: string, addDays = 0): string {
  const [year, month, day] = date.split("-").map(Number)
  const shifted = new Date(Date.UTC(year, month - 1, day + addDays))
  const shiftedDate = `${pad(shifted.getUTCFullYear(), 4)}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}`
  return zonedDateTimeToIso(shiftedDate, "00:00", timeZone)
}

/** Converts an API instant (UTC) into the local date and time for form inputs. */
export function isoToZonedParts(iso: string, timeZone: string): { date: string; time: string } {
  const w = wallClock(Date.parse(iso), timeZone)
  return { date: `${pad(w.year, 4)}-${pad(w.month)}-${pad(w.day)}`, time: `${pad(w.hour)}:${pad(w.minute)}` }
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"]

/** e.g. "1 Des 2026, 23.59". Formatted manually so output does not vary with ICU data. */
export function formatDateTime(iso: string | null, timeZone: string): string {
  if (!iso) return "-"
  const w = wallClock(Date.parse(iso), timeZone)
  return `${w.day} ${MONTHS[w.month - 1]} ${w.year}, ${pad(w.hour)}.${pad(w.minute)}`
}

function plural(distance: number): string {
  if (distance >= DAY_MS) return `${Math.floor(distance / DAY_MS)} hari`
  if (distance >= HOUR_MS) return `${Math.floor(distance / HOUR_MS)} jam`
  return `${Math.max(1, Math.floor(distance / MINUTE_MS))} menit`
}

/** Relative hint for a deadline. Completed tasks never count as overdue. */
export function describeDeadline(
  deadlineIso: string,
  status: TaskStatus,
  nowMs: number,
): { text: string; overdue: boolean } {
  if (status === "COMPLETED") return { text: "", overdue: false }
  const diff = Date.parse(deadlineIso) - nowMs
  if (diff < 0) return { text: `Terlambat ${plural(-diff)}`, overdue: true }
  return { text: `${plural(diff)} lagi`, overdue: false }
}
