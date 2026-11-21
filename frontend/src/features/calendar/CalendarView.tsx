"use client"

import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { useCallback, useMemo, useState } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { isoToZonedParts } from "@/features/tasks/datetime"
import { PRIORITIES, PRIORITY_LABELS } from "@/features/tasks/types"
import { useNow } from "@/features/tasks/useNow"
import { useUserTimeZone } from "@/features/tasks/useUserTimeZone"
import { describeApiError } from "@/lib/api-errors"
import { cn } from "@/lib/utils"

import { DayAgenda } from "./DayAgenda"
import { MonthGrid } from "./MonthGrid"
import { TaskDrawer } from "./TaskDrawer"
import { WeekView } from "./WeekView"
import { isValidDate, parseView, shiftAnchor, titleFor, visibleRange, type CalendarViewMode } from "./calendarDates"
import { groupByDay } from "./calendarTasks"
import { PRIORITY_MARKS } from "./priorityColor"
import { useCalendarTasks } from "./useCalendarTasks"

const VIEW_LABELS: Record<CalendarViewMode, string> = { month: "Bulan", week: "Minggu", day: "Hari" }

const todayIn = (timeZone: string, epochMs = Date.now()) => isoToZonedParts(new Date(epochMs).toISOString(), timeZone).date

function Legend() {
  return (
    <ul aria-label="Legenda prioritas" className="flex flex-wrap gap-x-3 gap-y-1 text-xs">
      {PRIORITIES.map((priority) => (
        <li key={priority} className="flex items-center gap-1.5">
          <span
            data-legend-swatch
            aria-hidden="true"
            className={cn("flex size-5 items-center justify-center rounded-sm border-l-4 text-[10px]", PRIORITY_MARKS[priority].chip, PRIORITY_MARKS[priority].accent)}
          >
            {PRIORITY_MARKS[priority].glyph}
          </span>
          {PRIORITY_LABELS[priority]}
        </li>
      ))}
    </ul>
  )
}

function CalendarSkeleton() {
  return (
    <div role="status" aria-label="Memuat kalender" className="flex flex-col gap-2">
      <Skeleton className="h-8 w-full" />
      <Skeleton className="h-72 w-full" />
    </div>
  )
}

/** View and anchor date live in the URL (?view=month&date=2026-11-21) so reload and back work. */
export function CalendarView() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const timeZone = useUserTimeZone()
  const now = useNow()

  const view = parseView(searchParams.get("view"))
  const dateParam = searchParams.get("date")
  const today = todayIn(timeZone, now)
  const date = isValidDate(dateParam) ? dateParam : today

  const range = useMemo(() => visibleRange(view, date, timeZone), [view, date, timeZone])
  const { data, error, isLoading, refetch } = useCalendarTasks(range.from, range.to)
  const byDay = useMemo(() => groupByDay(data ?? [], timeZone), [data, timeZone])
  const [openId, setOpenId] = useState<number | null>(null)
  const openTask = data?.find((task) => task.id === openId) ?? null

  const go = useCallback(
    (nextView: CalendarViewMode, nextDate: string) => {
      router.replace(`${pathname}?view=${nextView}&date=${nextDate}`, { scroll: false })
    },
    [router, pathname],
  )

  const unit = VIEW_LABELS[view]
  const shared = { selected: date, today, byDay, timeZone, onSelect: (next: string) => go(view, next), onOpen: (task: { id: number }) => setOpenId(task.id) }

  let body
  if (error && !data) {
    body = (
      <div role="alert" className="flex flex-col items-start gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4">
        <p className="text-sm text-destructive">{describeApiError(error)}</p>
        <Button variant="outline" onClick={refetch}>
          Coba lagi
        </Button>
      </div>
    )
  } else if (!data) {
    body = <CalendarSkeleton />
  } else {
    body = (
      <div aria-busy={isLoading} className={cn("transition-opacity", isLoading && "opacity-60")}>
        {view === "month" && (
          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
            <MonthGrid anchor={date} {...shared} />
            <DayAgenda date={date} tasks={byDay.get(date) ?? []} timeZone={timeZone} onOpen={shared.onOpen} className="self-start" />
          </div>
        )}
        {view === "week" && <WeekView anchor={date} {...shared} />}
        {view === "day" && <DayAgenda date={date} tasks={byDay.get(date) ?? []} timeZone={timeZone} onOpen={shared.onOpen} />}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" aria-label={`${unit} sebelumnya`} onClick={() => go(view, shiftAnchor(view, date, -1))}>
            <ChevronLeft aria-hidden="true" />
          </Button>
          <Button variant="outline" size="icon" aria-label={`${unit} berikutnya`} onClick={() => go(view, shiftAnchor(view, date, 1))}>
            <ChevronRight aria-hidden="true" />
          </Button>
          <Button variant="outline" onClick={() => go(view, todayIn(timeZone))}>
            Hari ini
          </Button>
        </div>
        <div role="group" aria-label="Tampilan kalender" className="flex gap-1">
          {(Object.keys(VIEW_LABELS) as CalendarViewMode[]).map((mode) => (
            <Button key={mode} variant={mode === view ? "default" : "outline"} aria-pressed={mode === view} onClick={() => go(mode, date)}>
              {VIEW_LABELS[mode]}
            </Button>
          ))}
        </div>
      </div>
      <h2 aria-live="polite" className="text-lg font-semibold tracking-tight">
        {titleFor(view, date)}
      </h2>
      <Legend />
      {body}
      <TaskDrawer task={openTask} timeZone={timeZone} onClose={() => setOpenId(null)} />
    </div>
  )
}
