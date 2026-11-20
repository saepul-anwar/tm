"use client"

import Link from "next/link"
import { useParams, useRouter } from "next/navigation"
import { useState } from "react"
import { Pencil, Trash2 } from "lucide-react"

import { Button, buttonVariants } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { ApiError } from "@/lib/api"
import { describeApiError } from "@/lib/api-errors"
import { cn } from "@/lib/utils"

import { DeleteTaskDialog } from "./DeleteTaskDialog"
import { PriorityBadge } from "./PriorityBadge"
import { TaskForm } from "./TaskForm"
import { describeDeadline, formatDateTime } from "./datetime"
import { STATUS_LABELS, type Task } from "./types"
import { useNow } from "./useNow"
import { useTask } from "./useTasks"
import { useUserTimeZone } from "./useUserTimeZone"

/** Reads `[id]` from the route; NaN when it is not a positive integer. */
function useTaskId(): number {
  const { id } = useParams<{ id: string }>()
  return /^\d+$/.test(id ?? "") ? Number(id) : Number.NaN
}

function TaskNotice({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const notFound = error instanceof ApiError && error.status === 404
  return (
    <div role="alert" className="flex flex-col items-start gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4">
      <p className="text-sm text-destructive">{notFound ? "Tugas tidak ditemukan. Mungkin sudah dihapus." : describeApiError(error)}</p>
      <div className="flex gap-2">
        {!notFound && (
          <Button variant="outline" onClick={onRetry}>
            Coba lagi
          </Button>
        )}
        <Link href="/tasks" className={cn(buttonVariants({ variant: "outline" }))}>
          Kembali ke daftar
        </Link>
      </div>
    </div>
  )
}

function LoadingBlock() {
  return (
    <div role="status" aria-label="Memuat tugas" data-testid="task-skeleton" className="flex flex-col gap-3">
      <Skeleton className="h-8 w-2/3" />
      <Skeleton className="h-4 w-1/3" />
      <Skeleton className="h-32 w-full" />
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 sm:grid sm:grid-cols-[10rem_1fr] sm:gap-3">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-sm break-words whitespace-pre-wrap">{children}</dd>
    </div>
  )
}

function Details({ task, timeZone }: { task: Task; timeZone: string }) {
  const hint = describeDeadline(task.deadline, task.status, useNow())
  return (
    <dl className="flex flex-col gap-3">
      <Row label="Prioritas">
        <PriorityBadge priority={task.priority} />
      </Row>
      <Row label="Status">{STATUS_LABELS[task.status]}</Row>
      <Row label="Mata kuliah">{task.subject ?? "-"}</Row>
      <Row label="Deadline">
        {formatDateTime(task.deadline, timeZone)} ({timeZone})
        {hint.text && <span className={cn("ml-2 text-xs", hint.overdue ? "font-medium text-destructive" : "text-muted-foreground")}>{hint.text}</span>}
      </Row>
      <Row label="Deskripsi">{task.description || "-"}</Row>
      <Row label="Selesai pada">{formatDateTime(task.completedAt, timeZone)}</Row>
      <Row label="Dibuat">{formatDateTime(task.createdAt, timeZone)}</Row>
      <Row label="Diperbarui">{formatDateTime(task.updatedAt, timeZone)}</Row>
    </dl>
  )
}

export function TaskDetail() {
  const id = useTaskId()
  const router = useRouter()
  const timeZone = useUserTimeZone()
  const { task, isLoading, error, refetch } = useTask(id)
  const [confirmDelete, setConfirmDelete] = useState(false)

  if (Number.isNaN(id)) return <TaskNotice error={new ApiError(404, "NOT_FOUND", "")} onRetry={refetch} />
  if (error) return <TaskNotice error={error} onRetry={refetch} />
  if (!task || isLoading) return <LoadingBlock />

  return (
    <article className="flex max-w-2xl flex-col gap-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="min-w-0 text-xl font-semibold tracking-tight break-words">{task.title}</h1>
        <div className="flex gap-2">
          <Link href={`/tasks/${task.id}/edit`} className={cn(buttonVariants({ variant: "outline" }))}>
            <Pencil aria-hidden="true" />
            Ubah
          </Link>
          <Button variant="destructive" onClick={() => setConfirmDelete(true)}>
            <Trash2 aria-hidden="true" />
            Hapus
          </Button>
        </div>
      </div>
      <Details task={task} timeZone={timeZone} />
      <Link href="/tasks" className="text-sm underline-offset-4 hover:underline">
        Kembali ke daftar
      </Link>
      <DeleteTaskDialog
        task={confirmDelete ? task : null}
        onClose={() => setConfirmDelete(false)}
        onDeleted={() => router.replace("/tasks")}
      />
    </article>
  )
}

export function TaskEdit() {
  const id = useTaskId()
  const { task, isLoading, error, refetch } = useTask(id)

  if (Number.isNaN(id)) return <TaskNotice error={new ApiError(404, "NOT_FOUND", "")} onRetry={refetch} />
  if (error) return <TaskNotice error={error} onRetry={refetch} />
  if (!task) return <LoadingBlock />

  return <TaskForm key={`${task.version}:${task.updatedAt}`} task={task} onReload={isLoading ? undefined : refetch} />
}
