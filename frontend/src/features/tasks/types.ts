export const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"] as const
export const STATUSES = ["TODO", "IN_PROGRESS", "COMPLETED"] as const

export type Priority = (typeof PRIORITIES)[number]
export type TaskStatus = (typeof STATUSES)[number]

export const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: "Rendah",
  MEDIUM: "Sedang",
  HIGH: "Tinggi",
  URGENT: "Mendesak",
}

export const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: "Belum dikerjakan",
  IN_PROGRESS: "Sedang dikerjakan",
  COMPLETED: "Selesai",
}

/** Mirrors the backend TaskResponse. All instants are UTC ISO-8601 strings. */
export type Task = {
  id: number
  title: string
  description: string | null
  subject: string | null
  deadline: string
  priority: Priority
  status: TaskStatus
  completedAt: string | null
  createdAt: string
  updatedAt: string
  version: number
  deadlineCategory: string | null
}

/** Body of POST/PUT /api/tasks. */
export type TaskPayload = {
  title: string
  description: string | null
  subject: string | null
  deadline: string
  priority: Priority
  status: TaskStatus
  version?: number
}
