import type { Task } from "@/features/tasks/types"

export type DashboardCounts = {
  total: number
  todo: number
  inProgress: number
  completed: number
  overdue: number
  upcoming: number
}

/** Mirrors the backend SummaryResponse (GET /api/dashboard/summary). */
export type DashboardSummary = {
  counts: DashboardCounts
  completionPercent: number
  dueSoon: Task[]
  topPriority: Task[]
}
