import { apiFetch } from "@/lib/api"
import type { Task } from "@/features/tasks/types"

import type { DashboardSummary } from "./types"

export const fetchSummary = (): Promise<DashboardSummary> => apiFetch<DashboardSummary>("/api/dashboard/summary")

export const fetchUpcoming = (): Promise<Task[]> => apiFetch<Task[]>("/api/dashboard/upcoming")

export const fetchOverdue = (): Promise<Task[]> => apiFetch<Task[]>("/api/dashboard/overdue")
