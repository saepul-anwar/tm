"use client"

import type { Task } from "@/features/tasks/types"
import { useApiQuery, type QueryState } from "@/features/tasks/useApiQuery"
import { useAuth } from "@/lib/auth/AuthProvider"

import { fetchOverdue, fetchSummary, fetchUpcoming } from "./dashboardApi"
import type { DashboardSummary } from "./types"

export type DashboardState = {
  summary: QueryState<DashboardSummary>
  upcoming: QueryState<Task[]>
  overdue: QueryState<Task[]>
}

/** Three independent requests so one failing section can be retried without reloading the rest. */
export function useDashboard(): DashboardState {
  const { status } = useAuth()
  const enabled = status === "authenticated"
  return {
    summary: useApiQuery("dashboard:summary", fetchSummary, enabled),
    upcoming: useApiQuery("dashboard:upcoming", fetchUpcoming, enabled),
    overdue: useApiQuery("dashboard:overdue", fetchOverdue, enabled),
  }
}
