import type { Priority, TaskStatus } from "@/features/tasks/types"

/** Mirrors the backend CalendarTaskResponse (GET /api/calendar/tasks). `deadline` is UTC. */
export type CalendarTask = {
  id: number
  title: string
  subject: string | null
  deadline: string
  priority: Priority
  status: TaskStatus
}
