"use client"

import Link from "next/link"
import { Plus } from "lucide-react"

import { Button, buttonVariants } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useUserTimeZone } from "@/features/tasks/useUserTimeZone"
import { describeApiError } from "@/lib/api-errors"

import { ProgressCard } from "./ProgressCard"
import { StatCards } from "./StatCards"
import { TaskMiniList } from "./TaskMiniList"
import { useDashboard } from "./useDashboard"

function DashboardSkeleton() {
  return (
    <div data-testid="dashboard-skeleton" role="status" aria-label="Memuat dashboard" className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-3 min-[480px]:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-20 w-full" />
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    </div>
  )
}

function SectionError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-start gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4">
      <p className="text-sm text-destructive">{describeApiError(error)}</p>
      <Button variant="outline" onClick={onRetry}>
        Coba lagi
      </Button>
    </div>
  )
}

function EmptyDashboard() {
  return (
    <div className="flex flex-col items-start gap-3 rounded-xl border border-dashed p-6">
      <h2 className="font-medium">Belum ada tugas</h2>
      <p className="text-sm text-muted-foreground">Catat tugas kuliah pertama Anda agar tidak terlewat.</p>
      <Link href="/tasks/new" className={buttonVariants()}>
        <Plus aria-hidden="true" />
        Buat tugas
      </Link>
    </div>
  )
}

export function DashboardView() {
  const { summary, upcoming, overdue } = useDashboard()
  const timeZone = useUserTimeZone()

  if (summary.error && !summary.data) return <SectionError error={summary.error} onRetry={summary.refetch} />
  if (!summary.data) return <DashboardSkeleton />
  const { counts, completionPercent, dueSoon, topPriority } = summary.data
  if (counts.total === 0) return <EmptyDashboard />

  const upcomingTasks = upcoming.data ?? []
  const overdueTasks = overdue.data ?? []

  return (
    <div className="flex flex-col gap-4">
      <StatCards counts={counts} />

      <div className="grid gap-4 lg:grid-cols-2">
        <ProgressCard completionPercent={completionPercent} counts={counts} />
        <TaskMiniList title="Segera diselesaikan" tasks={dueSoon} timeZone={timeZone} emptyText="Tidak ada deadline terdekat." />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <TaskMiniList title="Prioritas tertinggi" tasks={topPriority} timeZone={timeZone} emptyText="Tidak ada tugas aktif." />
        <TaskMiniList
          title="Deadline 7 hari ke depan"
          tasks={upcomingTasks}
          timeZone={timeZone}
          emptyText="Tidak ada deadline dalam 7 hari ke depan."
        >
          {upcoming.error && !upcoming.data ? (
            <SectionError error={upcoming.error} onRetry={upcoming.refetch} />
          ) : !upcoming.data ? (
            <Skeleton className="h-24 w-full" />
          ) : undefined}
        </TaskMiniList>
      </div>

      {overdue.error && !overdue.data ? (
        <TaskMiniList title="Terlambat" tasks={[]} timeZone={timeZone} emptyText="" danger>
          <SectionError error={overdue.error} onRetry={overdue.refetch} />
        </TaskMiniList>
      ) : (
        overdueTasks.length > 0 && <TaskMiniList title="Terlambat" tasks={overdueTasks} timeZone={timeZone} emptyText="" danger />
      )}
    </div>
  )
}
