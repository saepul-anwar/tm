import type { Task } from "./types"

// Synthetic data only. Shared by unit tests; never imported by app code.
export function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: 1,
    title: "Laporan praktikum contoh",
    description: "Deskripsi sintetis.",
    subject: "Fisika Dasar",
    deadline: "2026-12-01T16:59:00Z",
    priority: "HIGH",
    status: "TODO",
    completedAt: null,
    createdAt: "2026-11-20T01:00:00Z",
    updatedAt: "2026-11-20T02:00:00Z",
    version: 3,
    deadlineCategory: null,
    ...overrides,
  }
}

export function jsonResponse(body: unknown, status = 200): Response {
  if (status === 204) return new Response(null, { status })
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } })
}

export const okEnvelope = <T>(data: T, status = 200) => jsonResponse({ success: true, data }, status)

export const pageEnvelope = <T>(data: T[], totalElements = data.length, page = 0, size = 10) =>
  jsonResponse({
    success: true,
    data,
    meta: { page, size, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / size)) },
  })

export const errorEnvelope = (
  status: number,
  code: string,
  message = "Pesan server.",
  details: { field: string; message: string }[] = [],
) => jsonResponse({ success: false, error: { code, message, details } }, status)
