import { startOfZonedDayIso } from "./datetime"
import { PRIORITIES, STATUSES, type Priority, type TaskStatus } from "./types"

export const SORT_FIELDS = ["deadline", "priority", "createdAt", "completedAt"] as const
export type SortField = (typeof SORT_FIELDS)[number]
export type SortDirection = "asc" | "desc"

export const PAGE_SIZES = [10, 20, 50] as const
export const MAX_PAGE_SIZE = 50

/** Empty string means "no filter". Deadline bounds are local dates (YYYY-MM-DD) in the user's zone. */
export type TaskListFilters = {
  q: string
  subject: string
  status: TaskStatus | ""
  priority: Priority | ""
  deadlineFrom: string
  deadlineTo: string
  sort: SortField
  dir: SortDirection
  page: number
  size: number
}

export const DEFAULT_FILTERS: TaskListFilters = {
  q: "",
  subject: "",
  status: "",
  priority: "",
  deadlineFrom: "",
  deadlineTo: "",
  sort: "deadline",
  dir: "asc",
  page: 0,
  size: 10,
}

/** Completed page: fixed status, newest completion first. */
export const COMPLETED_DEFAULT_FILTERS: TaskListFilters = {
  ...DEFAULT_FILTERS,
  status: "COMPLETED",
  sort: "completedAt",
  dir: "desc",
}

const KEYS = Object.keys(DEFAULT_FILTERS) as (keyof TaskListFilters)[]
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

const oneOf = <T extends string>(allowed: readonly T[], value: string | null, fallback: T): T =>
  allowed.includes(value as T) ? (value as T) : fallback

function integer(value: string | null, fallback: number, min: number, max: number): number {
  const parsed = Number(value)
  return value !== null && Number.isInteger(parsed) && parsed >= min && parsed <= max ? parsed : fallback
}

const date = (value: string | null) => (value && DATE_PATTERN.test(value) ? value : "")

/** Reads filters from URL params; anything invalid falls back to `defaults`. */
export function parseTaskFilters(params: URLSearchParams, defaults: TaskListFilters): TaskListFilters {
  return {
    q: params.get("q") ?? defaults.q,
    subject: params.get("subject") ?? defaults.subject,
    status: oneOf<TaskStatus | "">([...STATUSES, ""], params.get("status"), defaults.status),
    priority: oneOf<Priority | "">([...PRIORITIES, ""], params.get("priority"), defaults.priority),
    deadlineFrom: params.has("deadlineFrom") ? date(params.get("deadlineFrom")) : defaults.deadlineFrom,
    deadlineTo: params.has("deadlineTo") ? date(params.get("deadlineTo")) : defaults.deadlineTo,
    sort: oneOf(SORT_FIELDS, params.get("sort"), defaults.sort),
    dir: oneOf<SortDirection>(["asc", "desc"], params.get("dir"), defaults.dir),
    page: integer(params.get("page"), defaults.page, 0, 100_000),
    size: integer(params.get("size"), defaults.size, 1, MAX_PAGE_SIZE),
  }
}

/** URL query string holding only the values that differ from `defaults`. */
export function toSearchString(filters: TaskListFilters, defaults: TaskListFilters): string {
  const params = new URLSearchParams()
  for (const key of KEYS) {
    if (filters[key] !== defaults[key]) params.set(key, String(filters[key]))
  }
  return params.toString()
}

/**
 * API query string. Dates become local-midnight instants with offset in `timeZone`;
 * `deadlineTo` is inclusive in the UI, so it is sent as the next local midnight
 * (the backend treats deadlineTo as exclusive). URLSearchParams encodes `+` as %2B.
 */
export function toApiQuery(filters: TaskListFilters, timeZone: string): string {
  const params = new URLSearchParams()
  if (filters.q.trim()) params.set("q", filters.q.trim())
  if (filters.subject) params.set("subject", filters.subject)
  if (filters.status) params.set("status", filters.status)
  if (filters.priority) params.set("priority", filters.priority)
  if (filters.deadlineFrom) params.set("deadlineFrom", startOfZonedDayIso(filters.deadlineFrom, timeZone))
  if (filters.deadlineTo) params.set("deadlineTo", startOfZonedDayIso(filters.deadlineTo, timeZone, 1))
  params.set("sort", filters.sort)
  params.set("dir", filters.dir)
  params.set("page", String(filters.page))
  params.set("size", String(filters.size))
  return params.toString()
}

export const hasActiveFilters = (filters: TaskListFilters, defaults: TaskListFilters): boolean =>
  (["q", "subject", "status", "priority", "deadlineFrom", "deadlineTo"] as const).some(
    (key) => filters[key] !== defaults[key],
  )
