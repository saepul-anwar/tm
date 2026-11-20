import { Select } from "@/components/ui/select"
import { cn } from "@/lib/utils"

import { STATUSES, STATUS_LABELS, type TaskStatus } from "./types"

type TaskStatusSelectProps = {
  value: TaskStatus
  label: string
  onChange: (status: TaskStatus) => void
  disabled?: boolean
  className?: string
}

/** Controlled status picker; the caller performs the PATCH. */
export function TaskStatusSelect({ value, label, onChange, disabled, className }: TaskStatusSelectProps) {
  return (
    <Select
      aria-label={label}
      value={value}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value as TaskStatus)}
      className={cn("h-8 w-auto min-w-40", className)}
    >
      {STATUSES.map((status) => (
        <option key={status} value={status}>
          {STATUS_LABELS[status]}
        </option>
      ))}
    </Select>
  )
}
