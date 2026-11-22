import { apiFetch } from "@/lib/api"

import type { CalendarTask } from "./types"

/** `from`/`to` are ISO-8601 with offset; encodeURIComponent turns `+` into %2B as the API requires. */
export const fetchCalendarTasks = (from: string, to: string): Promise<CalendarTask[]> =>
  apiFetch<CalendarTask[]>(`/api/calendar/tasks?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
