"use client"

import Link from "next/link"
import { useState } from "react"
import { Check, Pencil, Trash2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { describeApiError } from "@/lib/api-errors"
import { cn } from "@/lib/utils"

import { DeleteTaskDialog } from "./DeleteTaskDialog"
import { PriorityBadge } from "./PriorityBadge"
import { TaskStatusSelect } from "./TaskStatusSelect"
import { describeDeadline, formatDateTime } from "./datetime"
import { changeTaskStatus } from "./taskApi"
import { useNow } from "./useNow"
import type { Task, TaskStatus } from "./types"

type TaskListProps = {
  tasks: Task[]
  timeZone: string
  /** Called after a status change or delete so the parent can reload the list. */
  onChanged: () => void
}

function DeadlineCell({ task, timeZone, now }: { task: Task; timeZone: string; now: number }) {
  const hint = describeDeadline(task.deadline, task.status, now)
  return (
    <div className="flex flex-col">
      <span>{formatDateTime(task.deadline, timeZone)}</span>
      {hint.text && (
        <span className={cn("text-xs", hint.overdue ? "font-medium text-destructive" : "text-muted-foreground")}>{hint.text}</span>
      )}
    </div>
  )
}

export function TaskList({ tasks, timeZone, onChanged }: TaskListProps) {
  const now = useNow()
  const [pendingId, setPendingId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [toDelete, setToDelete] = useState<Task | null>(null)

  async function changeStatus(task: Task, status: TaskStatus) {
    if (status === task.status) return
    setPendingId(task.id)
    setError(null)
    try {
      await changeTaskStatus(task.id, status)
      onChanged()
    } catch (caught) {
      setError(describeApiError(caught))
    } finally {
      setPendingId(null)
    }
  }

  const statusSelect = (task: Task) => (
    <TaskStatusSelect
      value={task.status}
      label={`Status tugas ${task.title}`}
      disabled={pendingId === task.id}
      onChange={(status) => void changeStatus(task, status)}
    />
  )

  const actions = (task: Task) => (
    <div className="flex flex-wrap items-center gap-1.5">
      {task.status !== "COMPLETED" && (
        <Button
          variant="outline"
          size="sm"
          aria-label={`Tandai selesai: ${task.title}`}
          disabled={pendingId === task.id}
          onClick={() => void changeStatus(task, "COMPLETED")}
        >
          <Check aria-hidden="true" />
          Tandai selesai
        </Button>
      )}
      <Link
        href={`/tasks/${task.id}/edit`}
        aria-label={`Ubah: ${task.title}`}
        className="inline-flex size-7 items-center justify-center rounded-md hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      >
        <Pencil className="size-3.5" aria-hidden="true" />
      </Link>
      <Button variant="ghost" size="icon-sm" aria-label={`Hapus: ${task.title}`} onClick={() => setToDelete(task)}>
        <Trash2 aria-hidden="true" />
      </Button>
    </div>
  )

  return (
    <div className="flex flex-col gap-3">
      {error && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      )}

      <div className="hidden overflow-x-auto rounded-xl border md:block">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Daftar tugas</caption>
          <thead className="border-b bg-muted/50 text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="px-3 py-2 font-medium">Tugas</th>
              <th scope="col" className="px-3 py-2 font-medium">Prioritas</th>
              <th scope="col" className="px-3 py-2 font-medium">Deadline</th>
              <th scope="col" className="px-3 py-2 font-medium">Status</th>
              <th scope="col" className="px-3 py-2 font-medium">Aksi</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {tasks.map((task) => (
              <tr key={task.id} className="align-top">
                <td className="max-w-xs px-3 py-2.5">
                  <Link href={`/tasks/${task.id}`} className="font-medium break-words hover:underline">
                    {task.title}
                  </Link>
                  {task.subject && <p className="text-xs text-muted-foreground">{task.subject}</p>}
                </td>
                <td className="px-3 py-2.5">
                  <PriorityBadge priority={task.priority} />
                </td>
                <td className="px-3 py-2.5 whitespace-nowrap">
                  <DeadlineCell task={task} timeZone={timeZone} now={now} />
                </td>
                <td className="px-3 py-2.5">{statusSelect(task)}</td>
                <td className="px-3 py-2.5">{actions(task)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul className="flex flex-col gap-3 md:hidden" aria-label="Daftar tugas">
        {tasks.map((task) => (
          <li key={task.id} className="flex flex-col gap-2.5 rounded-xl border p-3">
            <div className="flex items-start justify-between gap-2">
              <Link href={`/tasks/${task.id}`} className="min-w-0 font-medium break-words hover:underline">
                {task.title}
              </Link>
              <PriorityBadge priority={task.priority} />
            </div>
            {task.subject && <p className="text-xs text-muted-foreground">{task.subject}</p>}
            <div className="text-sm">
              <DeadlineCell task={task} timeZone={timeZone} now={now} />
            </div>
            {statusSelect(task)}
            {actions(task)}
          </li>
        ))}
      </ul>

      <DeleteTaskDialog
        task={toDelete}
        onClose={() => setToDelete(null)}
        onDeleted={() => {
          setToDelete(null)
          onChanged()
        }}
      />
    </div>
  )
}
