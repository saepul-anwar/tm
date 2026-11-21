"use client"

import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog"
import { describeApiError } from "@/lib/api-errors"

import { deleteTask } from "./taskApi"
import type { Task } from "./types"

type DeleteTaskDialogProps = {
  /** The task awaiting confirmation; null keeps the dialog closed. */
  task: Task | null
  onClose: () => void
  onDeleted: (task: Task) => void
}

export function DeleteTaskDialog({ task, onClose, onDeleted }: DeleteTaskDialogProps) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function close() {
    if (pending) return
    setError(null)
    onClose()
  }

  async function confirm() {
    if (!task) return
    setPending(true)
    setError(null)
    try {
      await deleteTask(task.id)
      onDeleted(task)
    } catch (caught) {
      setError(describeApiError(caught))
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open={task !== null} onOpenChange={(open) => !open && close()}>
      {task && (
        <DialogContent>
          <DialogTitle>Hapus tugas?</DialogTitle>
          <DialogDescription>
            Tugas &quot;{task.title}&quot; akan dihapus permanen dan tidak bisa dikembalikan.
          </DialogDescription>
          {error && (
            <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={close} disabled={pending}>
              Batal
            </Button>
            <Button variant="destructive" onClick={() => void confirm()} disabled={pending}>
              {pending ? "Menghapus..." : "Hapus"}
            </Button>
          </div>
        </DialogContent>
      )}
    </Dialog>
  )
}
