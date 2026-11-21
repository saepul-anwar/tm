import { cn } from "@/lib/utils"

import { PRIORITY_LABELS, type Priority } from "./types"

// Distinct hues; the text label keeps priority readable without color.
export const PRIORITY_BADGE_STYLES: Record<Priority, string> = {
  LOW: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300",
  MEDIUM: "bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300",
  HIGH: "bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-300",
  URGENT: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
}

export function PriorityBadge({ priority }: { priority: Priority }) {
  return (
    <span
      data-priority={priority}
      className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap", PRIORITY_BADGE_STYLES[priority])}
    >
      {PRIORITY_LABELS[priority]}
    </span>
  )
}
