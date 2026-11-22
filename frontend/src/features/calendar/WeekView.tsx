import { cn } from "@/lib/utils"

import { longDate, weekdayName, weekDays, dayOfMonth } from "./calendarDates"
import { tasksLabel } from "./calendarTasks"
import { TaskRow } from "./TaskButtons"
import type { CalendarTask } from "./types"

type WeekViewProps = {
  anchor: string
  selected: string
  today: string
  byDay: Map<string, CalendarTask[]>
  timeZone: string
  onSelect: (date: string) => void
  onOpen: (task: CalendarTask) => void
}

/** Seven days, Monday first: stacked on narrow screens, side by side from `lg`. Tasks are listed in time order. */
export function WeekView({ anchor, selected, today, byDay, timeZone, onSelect, onOpen }: WeekViewProps) {
  return (
    <div className="grid grid-cols-1 gap-2 lg:grid-cols-7">
      {weekDays(anchor).map((date) => {
        const tasks = byDay.get(date) ?? []
        const isToday = date === today
        return (
          <section
            key={date}
            aria-label={`Tugas ${longDate(date)}`}
            className={cn("flex min-w-0 flex-col gap-2 rounded-xl border p-2", date === selected && "ring-2 ring-primary")}
          >
            <button
              type="button"
              aria-label={`${longDate(date)}, ${tasksLabel(tasks.length)}`}
              aria-current={isToday ? "date" : undefined}
              aria-pressed={date === selected}
              onClick={() => onSelect(date)}
              className="flex items-center gap-2 rounded-md px-1 py-1 text-left text-sm font-medium outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
            >
              <span
                className={cn(
                  "flex size-6 items-center justify-center rounded-full text-xs",
                  isToday && "bg-primary text-primary-foreground",
                )}
              >
                {dayOfMonth(date)}
              </span>
              {weekdayName(date)}
            </button>
            {tasks.length === 0 ? (
              <p className="px-1 text-xs text-muted-foreground">Tidak ada tugas.</p>
            ) : (
              <ul className="flex flex-col gap-1.5">
                {tasks.map((task) => (
                  <li key={task.id}>
                    <TaskRow task={task} timeZone={timeZone} onOpen={onOpen} />
                  </li>
                ))}
              </ul>
            )}
          </section>
        )
      })}
    </div>
  )
}
