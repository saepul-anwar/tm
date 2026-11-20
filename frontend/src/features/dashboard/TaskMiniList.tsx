import Link from "next/link"
import type { ReactNode } from "react"

import { PriorityBadge } from "@/features/tasks/PriorityBadge"
import { formatDateTime } from "@/features/tasks/datetime"
import type { Task } from "@/features/tasks/types"
import { cn } from "@/lib/utils"

// Calm hues; the text label carries the meaning, color is secondary.
const CATEGORIES: Record<string, { label: string; style: string }> = {
  OVERDUE: { label: "Terlambat", style: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300" },
  DUE_TODAY: { label: "Hari ini", style: "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300" },
  DUE_TOMORROW: { label: "Besok", style: "bg-yellow-50 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-300" },
  UPCOMING: { label: "Mendatang", style: "bg-indigo-100 text-indigo-800 dark:bg-indigo-950 dark:text-indigo-300" },
  LATER: { label: "Nanti", style: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300" },
}

function CategoryChip({ category }: { category: string | null }) {
  const config = category ? CATEGORIES[category] : undefined
  if (!category || !config) return null
  return (
    <span
      data-testid="category-chip"
      data-category={category}
      className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap", config.style)}
    >
      {config.label}
    </span>
  )
}

type TaskMiniListProps = {
  title: string
  tasks: Task[]
  timeZone: string
  emptyText: string
  /** Red accent for the overdue section. */
  danger?: boolean
  /** Replaces the list, e.g. a per-section error with retry. */
  children?: ReactNode
}

export function TaskMiniList({ title, tasks, timeZone, emptyText, danger = false, children }: TaskMiniListProps) {
  return (
    <section
      aria-label={title}
      data-danger={danger ? "true" : undefined}
      className={cn("flex flex-col gap-3 rounded-xl border p-4", danger && "border-destructive/40 bg-destructive/5")}
    >
      <h2 className={cn("font-medium", danger && "text-destructive")}>{title}</h2>
      {children ??
        (tasks.length === 0 ? (
          <p className="text-sm text-muted-foreground">{emptyText}</p>
        ) : (
          <ul className="flex flex-col divide-y">
            {tasks.map((task) => (
              <li key={task.id} className="flex flex-col gap-1 py-2.5 first:pt-0 last:pb-0">
                <div className="flex items-start justify-between gap-2">
                  <Link href={`/tasks/${task.id}`} className="min-w-0 font-medium break-words hover:underline">
                    {task.title}
                  </Link>
                  <PriorityBadge priority={task.priority} />
                </div>
                {task.subject && <p className="text-xs text-muted-foreground">{task.subject}</p>}
                <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  <span>{formatDateTime(task.deadline, timeZone)}</span>
                  <CategoryChip category={task.deadlineCategory} />
                </div>
              </li>
            ))}
          </ul>
        ))}
    </section>
  )
}
