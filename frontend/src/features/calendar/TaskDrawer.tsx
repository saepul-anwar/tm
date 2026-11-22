"use client"

import Link from "next/link"

import { buttonVariants } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog"
import { PriorityBadge } from "@/features/tasks/PriorityBadge"
import { formatDateTime } from "@/features/tasks/datetime"
import { STATUS_LABELS } from "@/features/tasks/types"
import { cn } from "@/lib/utils"

import type { CalendarTask } from "./types"

type TaskDrawerProps = { task: CalendarTask | null; timeZone: string; onClose: () => void }

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 sm:grid sm:grid-cols-[7rem_1fr] sm:gap-3">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-sm break-words">{children}</dd>
    </div>
  )
}

/** Modal detail of one task (focus is trapped; Escape closes). Full editing lives on the task pages. */
export function TaskDrawer({ task, timeZone, onClose }: TaskDrawerProps) {
  return (
    <Dialog open={task !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {task && (
          <>
            <DialogTitle className="break-words">{task.title}</DialogTitle>
            <DialogDescription className="sr-only">Ringkasan tugas</DialogDescription>
            <dl className="flex flex-col gap-3">
              <Row label="Mata kuliah">{task.subject ?? "-"}</Row>
              <Row label="Deadline">
                {formatDateTime(task.deadline, timeZone)} ({timeZone})
              </Row>
              <Row label="Prioritas">
                <PriorityBadge priority={task.priority} />
              </Row>
              <Row label="Status">{STATUS_LABELS[task.status]}</Row>
            </dl>
            <div className="flex flex-wrap justify-end gap-2">
              <Link href={`/tasks/${task.id}/edit`} className={cn(buttonVariants({ variant: "outline" }))}>
                Ubah
              </Link>
              <Link href={`/tasks/${task.id}`} className={cn(buttonVariants())}>
                Buka detail
              </Link>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
