"use client"

import { useAuth } from "@/lib/auth/AuthProvider"
import { useApiQuery, type QueryState } from "@/features/tasks/useApiQuery"

import { fetchCalendarTasks } from "./calendarApi"
import type { CalendarTask } from "./types"

/** Tasks of the visible range [from, to). Nothing is requested until the user is authenticated. */
export function useCalendarTasks(from: string, to: string): QueryState<CalendarTask[]> {
  const { status } = useAuth()
  return useApiQuery(`calendar:${from}|${to}`, () => fetchCalendarTasks(from, to), status === "authenticated")
}
