"use client"

import { useEffect, useState, type ReactNode } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select } from "@/components/ui/select"

import type { SortField, TaskListFilters } from "./filters"
import { PRIORITIES, PRIORITY_LABELS, STATUSES, STATUS_LABELS, type Priority, type TaskStatus } from "./types"

export const SEARCH_DEBOUNCE_MS = 300

const SORT_LABELS: Record<SortField, string> = {
  deadline: "Deadline",
  priority: "Prioritas",
  createdAt: "Tanggal dibuat",
  completedAt: "Tanggal selesai",
}

type TaskFiltersProps = {
  filters: TaskListFilters
  subjects: string[]
  onChange: (patch: Partial<TaskListFilters>) => void
  onReset: () => void
  /** The Completed page fixes the status, so the status filter is hidden there. */
  showStatus?: boolean
  sortFields?: readonly SortField[]
}

function Field({ id, label, children }: { id: string; label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
    </div>
  )
}

export function TaskFilters({
  filters,
  subjects,
  onChange,
  onReset,
  showStatus = true,
  sortFields = ["deadline", "priority", "createdAt"],
}: TaskFiltersProps) {
  const [text, setText] = useState(filters.q)
  const [syncedQ, setSyncedQ] = useState(filters.q)

  // Follow the URL when it changes from outside (reset, back button).
  if (filters.q !== syncedQ) {
    setSyncedQ(filters.q)
    setText(filters.q)
  }

  useEffect(() => {
    if (text === filters.q) return
    const timer = setTimeout(() => onChange({ q: text }), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [text, filters.q, onChange])

  // A subject that is no longer returned by the API (e.g. its last task was deleted) must stay selectable.
  const subjectOptions = filters.subject && !subjects.includes(filters.subject) ? [filters.subject, ...subjects] : subjects

  return (
    <form
      role="search"
      aria-label="Filter tugas"
      onSubmit={(event) => event.preventDefault()}
      className="grid grid-cols-1 gap-3 rounded-xl border p-3 sm:grid-cols-2 lg:grid-cols-4"
    >
      <Field id="filter-q" label="Cari judul">
        <Input id="filter-q" type="search" value={text} onChange={(event) => setText(event.target.value)} placeholder="Kata kunci judul" />
      </Field>
      {showStatus && (
        <Field id="filter-status" label="Status">
          <Select
            id="filter-status"
            value={filters.status}
            onChange={(event) => onChange({ status: event.target.value as TaskStatus | "" })}
          >
            <option value="">Semua status</option>
            {STATUSES.map((status) => (
              <option key={status} value={status}>
                {STATUS_LABELS[status]}
              </option>
            ))}
          </Select>
        </Field>
      )}
      <Field id="filter-priority" label="Prioritas">
        <Select
          id="filter-priority"
          value={filters.priority}
          onChange={(event) => onChange({ priority: event.target.value as Priority | "" })}
        >
          <option value="">Semua prioritas</option>
          {PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {PRIORITY_LABELS[priority]}
            </option>
          ))}
        </Select>
      </Field>
      <Field id="filter-subject" label="Mata kuliah">
        <Select id="filter-subject" value={filters.subject} onChange={(event) => onChange({ subject: event.target.value })}>
          <option value="">Semua mata kuliah</option>
          {subjectOptions.map((subject) => (
            <option key={subject} value={subject}>
              {subject}
            </option>
          ))}
        </Select>
      </Field>
      <Field id="filter-from" label="Deadline dari">
        <Input id="filter-from" type="date" value={filters.deadlineFrom} onChange={(event) => onChange({ deadlineFrom: event.target.value })} />
      </Field>
      <Field id="filter-to" label="Deadline sampai">
        <Input id="filter-to" type="date" value={filters.deadlineTo} onChange={(event) => onChange({ deadlineTo: event.target.value })} />
      </Field>
      <Field id="filter-sort" label="Urutkan">
        <Select id="filter-sort" value={filters.sort} onChange={(event) => onChange({ sort: event.target.value as SortField })}>
          {sortFields.map((field) => (
            <option key={field} value={field}>
              {SORT_LABELS[field]}
            </option>
          ))}
        </Select>
      </Field>
      <Field id="filter-dir" label="Arah">
        <Select id="filter-dir" value={filters.dir} onChange={(event) => onChange({ dir: event.target.value as "asc" | "desc" })}>
          <option value="asc">Menaik</option>
          <option value="desc">Menurun</option>
        </Select>
      </Field>
      <div className="flex items-end sm:col-span-2 lg:col-span-4 lg:justify-end">
        <Button type="button" variant="outline" onClick={onReset}>
          Reset filter
        </Button>
      </div>
    </form>
  )
}
