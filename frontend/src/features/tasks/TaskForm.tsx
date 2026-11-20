"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState, type ReactNode } from "react"
import { useForm } from "react-hook-form"
import type { FieldError } from "react-hook-form"

import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { ApiError } from "@/lib/api"
import { describeApiError } from "@/lib/api-errors"
import { cn } from "@/lib/utils"

import { isoToZonedParts } from "./datetime"
import { createTask, updateTask } from "./taskApi"
import { taskSchema, toTaskPayload, type TaskFormValues } from "./taskSchema"
import { PRIORITIES, PRIORITY_LABELS, STATUSES, STATUS_LABELS, type Task } from "./types"
import { useUserTimeZone } from "./useUserTimeZone"

export const CONFLICT_MESSAGE =
  "Tugas ini sudah diubah di tempat lain sejak Anda membukanya. Muat ulang data untuk melihat versi terbaru; perubahan yang belum disimpan di form ini akan hilang."

// Backend field name -> form field name (the deadline is split into date + time in the form).
const SERVER_FIELDS: Record<string, keyof TaskFormValues> = {
  title: "title",
  description: "description",
  subject: "subject",
  deadline: "deadlineDate",
  priority: "priority",
  status: "status",
}

function Field({ id, label, error, hint, children }: { id: string; label: string; error?: FieldError; hint?: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && !error && <p className="text-xs text-muted-foreground">{hint}</p>}
      {error?.message && (
        <p id={`${id}-error`} className="text-sm text-destructive">
          {error.message}
        </p>
      )}
    </div>
  )
}

const a11y = (id: string, error?: FieldError) => ({
  id,
  "aria-invalid": error ? (true as const) : undefined,
  "aria-describedby": error ? `${id}-error` : undefined,
})

type TaskFormProps = {
  /** Present in edit mode. */
  task?: Task
  /** Edit mode only: reload the task after a version conflict. */
  onReload?: () => void
}

function defaultValues(task: Task | undefined, timeZone: string): TaskFormValues {
  if (!task) {
    return { title: "", description: "", subject: "", deadlineDate: "", deadlineTime: "", priority: "MEDIUM", status: "TODO" }
  }
  const { date, time } = isoToZonedParts(task.deadline, timeZone)
  return {
    title: task.title,
    description: task.description ?? "",
    subject: task.subject ?? "",
    deadlineDate: date,
    deadlineTime: time,
    priority: task.priority,
    status: task.status,
  }
}

export function TaskForm({ task, onReload }: TaskFormProps) {
  const router = useRouter()
  const timeZone = useUserTimeZone()
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TaskFormValues>({
    resolver: zodResolver(taskSchema),
    defaultValues: defaultValues(task, timeZone),
  })

  async function onSubmit(values: TaskFormValues) {
    setFormError(null)
    setConflict(false)
    try {
      if (task) {
        await updateTask(task.id, toTaskPayload(values, timeZone, task.version))
        router.push(`/tasks/${task.id}`)
      } else {
        await createTask(toTaskPayload(values, timeZone))
        router.push("/tasks")
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setConflict(true)
        return
      }
      const mapped = error instanceof ApiError ? error.details.filter((detail) => detail.field in SERVER_FIELDS) : []
      mapped.forEach((detail) => setError(SERVER_FIELDS[detail.field], { type: "server", message: detail.message }))
      setFormError(mapped.length > 0 ? null : describeApiError(error))
    }
  }

  const title = register("title")
  const editing = task !== undefined

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex max-w-2xl flex-col gap-4">
      <Field id="title" label="Judul" error={errors.title}>
        <Input {...a11y("title", errors.title)} autoComplete="off" {...title} />
      </Field>
      <Field id="description" label="Deskripsi" error={errors.description}>
        <Textarea {...a11y("description", errors.description)} rows={4} {...register("description")} />
      </Field>
      <Field id="subject" label="Mata kuliah" error={errors.subject}>
        <Input {...a11y("subject", errors.subject)} autoComplete="off" {...register("subject")} />
      </Field>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field id="deadlineDate" label="Tanggal deadline" error={errors.deadlineDate} hint={`Zona waktu: ${timeZone}`}>
          <Input {...a11y("deadlineDate", errors.deadlineDate)} type="date" {...register("deadlineDate")} />
        </Field>
        <Field id="deadlineTime" label="Jam deadline" error={errors.deadlineTime}>
          <Input {...a11y("deadlineTime", errors.deadlineTime)} type="time" {...register("deadlineTime")} />
        </Field>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field id="priority" label="Prioritas" error={errors.priority}>
          <Select {...a11y("priority", errors.priority)} {...register("priority")}>
            {PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>
                {PRIORITY_LABELS[priority]}
              </option>
            ))}
          </Select>
        </Field>
        <Field id="status" label="Status" error={errors.status}>
          <Select {...a11y("status", errors.status)} {...register("status")}>
            {STATUSES.map((status) => (
              <option key={status} value={status}>
                {STATUS_LABELS[status]}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {conflict && (
        <div role="alert" className="flex flex-col items-start gap-2 rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          <p>{CONFLICT_MESSAGE}</p>
          {onReload && (
            <Button type="button" variant="outline" onClick={onReload}>
              Muat ulang data
            </Button>
          )}
        </div>
      )}
      {formError && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {formError}
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Menyimpan..." : editing ? "Simpan perubahan" : "Simpan tugas"}
        </Button>
        <Link href={editing ? `/tasks/${task.id}` : "/tasks"} className={cn(buttonVariants({ variant: "outline" }))}>
          Batal
        </Link>
      </div>
    </form>
  )
}
