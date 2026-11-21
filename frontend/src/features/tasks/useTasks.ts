"use client"

import type { PageMeta } from "@/lib/api"
import { useAuth } from "@/lib/auth/AuthProvider"

import { toApiQuery, type TaskListFilters } from "./filters"
import { fetchSubjects, fetchTask, listTasks } from "./taskApi"
import type { Task } from "./types"
import { useApiQuery } from "./useApiQuery"
import { useUserTimeZone } from "./useUserTimeZone"

export type UseTasksResult = {
  data: Task[] | null
  meta: PageMeta | null
  isLoading: boolean
  error: unknown
  refetch: () => void
}

type TaskPage = { data: Task[]; meta: PageMeta }

export function useTasks(filters: TaskListFilters): UseTasksResult {
  const { status } = useAuth()
  const timeZone = useUserTimeZone()
  const query = toApiQuery(filters, timeZone)
  const result = useApiQuery<TaskPage>(query, () => listTasks(query), status === "authenticated")
  return {
    data: result.data?.data ?? null,
    meta: result.data?.meta ?? null,
    isLoading: result.isLoading,
    error: result.error,
    refetch: result.refetch,
  }
}

export function useTask(id: number) {
  const { status } = useAuth()
  const result = useApiQuery<Task>(`task:${id}`, () => fetchTask(id), status === "authenticated")
  return { task: result.data, isLoading: result.isLoading, error: result.error, refetch: result.refetch }
}

/** Distinct subjects of the current user, for the filter dropdown. A failure just yields an empty list. */
export function useSubjects(): string[] {
  const { status } = useAuth()
  const result = useApiQuery<string[]>("subjects", () => fetchSubjects(), status === "authenticated")
  return result.data ?? []
}
