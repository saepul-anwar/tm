"use client"

import Link from "next/link"
import { Plus } from "lucide-react"

import { Button, buttonVariants } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { describeApiError } from "@/lib/api-errors"
import { cn } from "@/lib/utils"

import { Pagination } from "./Pagination"
import { TaskFilters } from "./TaskFilters"
import { TaskList } from "./TaskList"
import { COMPLETED_DEFAULT_FILTERS, DEFAULT_FILTERS, hasActiveFilters, type SortField } from "./filters"
import { useSubjects, useTasks } from "./useTasks"
import { useTaskFilterState } from "./useTaskFilterState"
import { useUserTimeZone } from "./useUserTimeZone"

const ALL_SORTS: readonly SortField[] = ["deadline", "priority", "createdAt"]
const COMPLETED_SORTS: readonly SortField[] = ["completedAt", "deadline", "priority", "createdAt"]

function ListSkeleton() {
  return (
    <div data-testid="task-skeleton" role="status" aria-label="Memuat tugas" className="flex flex-col gap-2">
      {Array.from({ length: 5 }, (_, index) => (
        <Skeleton key={index} className="h-14 w-full" />
      ))}
    </div>
  )
}

type TaskListViewProps = { variant?: "all" | "completed" }

export function TaskListView({ variant = "all" }: TaskListViewProps) {
  const completed = variant === "completed"
  const defaults = completed ? COMPLETED_DEFAULT_FILTERS : DEFAULT_FILTERS
  const { filters: parsed, update, reset } = useTaskFilterState(defaults)
  // The Completed page always shows completed tasks, whatever the URL says.
  const filters = completed ? { ...parsed, status: "COMPLETED" as const } : parsed
  const { data, meta, isLoading, error, refetch } = useTasks(filters)
  const subjects = useSubjects()
  const timeZone = useUserTimeZone()
  const filtered = hasActiveFilters(filters, defaults)

  let content
  if (error && !data) {
    content = (
      <div role="alert" className="flex flex-col items-start gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4">
        <p className="text-sm text-destructive">{describeApiError(error)}</p>
        <Button variant="outline" onClick={refetch}>
          Coba lagi
        </Button>
      </div>
    )
  } else if (isLoading && !data) {
    content = <ListSkeleton />
  } else if (data && data.length === 0) {
    content = filtered ? (
      <div className="flex flex-col items-start gap-3 rounded-xl border border-dashed p-6">
        <p className="text-sm text-muted-foreground">Tidak ada tugas yang cocok dengan filter.</p>
        <Button variant="outline" onClick={reset}>
          Reset filter
        </Button>
      </div>
    ) : (
      <div className="flex flex-col items-start gap-3 rounded-xl border border-dashed p-6">
        <h2 className="font-medium">{completed ? "Belum ada tugas selesai" : "Belum ada tugas"}</h2>
        <p className="text-sm text-muted-foreground">
          {completed ? "Tugas yang sudah selesai akan muncul di sini." : "Catat tugas kuliah pertama Anda agar tidak terlewat."}
        </p>
        <Link href="/tasks/new" className={cn(buttonVariants())}>
          <Plus aria-hidden="true" />
          Buat tugas
        </Link>
      </div>
    )
  } else if (data) {
    content = (
      <>
        <div aria-busy={isLoading} className={cn(isLoading && "opacity-60 transition-opacity")}>
          <TaskList tasks={data} timeZone={timeZone} onChanged={refetch} />
        </div>
        {meta && <Pagination meta={meta} onPageChange={(page) => update({ page })} />}
      </>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <TaskFilters
        filters={filters}
        subjects={subjects}
        onChange={update}
        onReset={reset}
        showStatus={!completed}
        sortFields={completed ? COMPLETED_SORTS : ALL_SORTS}
      />
      {content}
    </div>
  )
}
