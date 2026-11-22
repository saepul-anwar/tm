import { isoToZonedParts } from "@/features/tasks/datetime"

import type { CalendarTask } from "./types"

/** Local time of a deadline as "08.00", in the user's zone. */
export function formatTime(iso: string, timeZone: string): string {
  return isoToZonedParts(iso, timeZone).time.replace(":", ".")
}

/** Buckets tasks by their local calendar day (zone of the profile), each bucket in time order. */
export function groupByDay(tasks: CalendarTask[], timeZone: string): Map<string, CalendarTask[]> {
  const sorted = [...tasks].sort((a, b) => Date.parse(a.deadline) - Date.parse(b.deadline) || a.id - b.id)
  const byDay = new Map<string, CalendarTask[]>()
  for (const task of sorted) {
    const day = isoToZonedParts(task.deadline, timeZone).date
    const bucket = byDay.get(day)
    if (bucket) bucket.push(task)
    else byDay.set(day, [task])
  }
  return byDay
}

export const tasksLabel = (count: number) => (count === 0 ? "tidak ada tugas" : `${count} tugas`)
