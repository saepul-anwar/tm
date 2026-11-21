import { apiFetch, apiFetchPage, type PageMeta } from "@/lib/api"

import type { Task, TaskPayload, TaskStatus } from "./types"

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) })

export const listTasks = (query: string): Promise<{ data: Task[]; meta: PageMeta }> =>
  apiFetchPage<Task[]>(`/api/tasks?${query}`)

export const fetchTask = (id: number): Promise<Task> => apiFetch<Task>(`/api/tasks/${id}`)

export const fetchSubjects = (): Promise<string[]> => apiFetch<string[]>("/api/tasks/subjects")

export const createTask = (payload: TaskPayload): Promise<Task> => apiFetch<Task>("/api/tasks", json("POST", payload))

export const updateTask = (id: number, payload: TaskPayload): Promise<Task> =>
  apiFetch<Task>(`/api/tasks/${id}`, json("PUT", payload))

export const changeTaskStatus = (id: number, status: TaskStatus): Promise<Task> =>
  apiFetch<Task>(`/api/tasks/${id}/status`, json("PATCH", { status }))

export const deleteTask = (id: number): Promise<void> => apiFetch<void>(`/api/tasks/${id}`, { method: "DELETE" })
