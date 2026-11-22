import { PriorityBadge } from "@/features/tasks/PriorityBadge"
import { PRIORITY_LABELS } from "@/features/tasks/types"
import { cn } from "@/lib/utils"

import { formatTime } from "./calendarTasks"
import { PRIORITY_MARKS } from "./priorityColor"
import type { CalendarTask } from "./types"

type TaskButtonProps = { task: CalendarTask; timeZone: string; onOpen: (task: CalendarTask) => void }

const FOCUS = "outline-none focus-visible:ring-2 focus-visible:ring-ring"
const isCompleted = (task: CalendarTask) => task.status === "COMPLETED"

/** Compact chip for month cells: truncated title, color + shape + accessible priority text. */
export function TaskChip({ task, timeZone, onOpen }: TaskButtonProps) {
  const mark = PRIORITY_MARKS[task.priority]
  const completed = isCompleted(task)
  return (
    <button
      type="button"
      data-priority={task.priority}
      data-status={task.status}
      aria-label={`${task.title}, prioritas ${PRIORITY_LABELS[task.priority]}, ${formatTime(task.deadline, timeZone)}${completed ? ", selesai" : ""}`}
      onClick={() => onOpen(task)}
      className={cn(
        "flex w-full min-w-0 items-center gap-1 rounded-sm border-l-4 px-1 py-0.5 text-left text-xs",
        mark.chip,
        mark.accent,
        completed && "opacity-60",
        FOCUS,
      )}
    >
      <span aria-hidden="true" className="shrink-0 text-[10px] leading-none">
        {mark.glyph}
      </span>
      <span className={cn("truncate", completed && "line-through")}>{task.title}</span>
    </button>
  )
}

/** Full row for lists (day panel, week columns, day agenda). */
export function TaskRow({ task, timeZone, onOpen }: TaskButtonProps) {
  const mark = PRIORITY_MARKS[task.priority]
  const completed = isCompleted(task)
  return (
    <button
      type="button"
      data-priority={task.priority}
      data-status={task.status}
      onClick={() => onOpen(task)}
      className={cn(
        "flex w-full min-w-0 flex-col gap-0.5 rounded-md border border-l-4 bg-background px-2.5 py-2 text-left hover:bg-muted",
        mark.accent,
        completed && "opacity-60",
        FOCUS,
      )}
    >
      <span className="flex flex-wrap items-center justify-between gap-1">
        <span className="text-xs font-medium text-muted-foreground tabular-nums">{formatTime(task.deadline, timeZone)}</span>
        <PriorityBadge priority={task.priority} />
      </span>
      <span className={cn("text-sm font-medium break-words", completed && "line-through")}>{task.title}</span>
      {task.subject && <span className="text-xs break-words text-muted-foreground">{task.subject}</span>}
    </button>
  )
}
