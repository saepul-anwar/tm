import { cn } from "@/lib/utils"

import { buildMonthGrid, dayOfMonth, longDate, monthKey, WEEKDAY_NAMES, WEEKDAY_SHORT } from "./calendarDates"
import { tasksLabel } from "./calendarTasks"
import { PRIORITY_MARKS } from "./priorityColor"
import { TaskChip } from "./TaskButtons"
import type { CalendarTask } from "./types"

const MAX_CHIPS = 3
const MAX_DOTS = 3

type MonthGridProps = {
  anchor: string
  selected: string
  today: string
  byDay: Map<string, CalendarTask[]>
  timeZone: string
  onSelect: (date: string) => void
  onOpen: (task: CalendarTask) => void
}

/** Priority shapes for narrow screens, where titles do not fit. Decorative: the date button names the count. */
function Dots({ tasks }: { tasks: CalendarTask[] }) {
  return (
    <span aria-hidden="true" className="flex min-h-3 flex-wrap items-center justify-center gap-0.5 sm:hidden">
      {tasks.slice(0, MAX_DOTS).map((task) => (
        <span key={task.id} className={cn("text-[10px] leading-none", PRIORITY_MARKS[task.priority].glyphColor)}>
          {PRIORITY_MARKS[task.priority].glyph}
        </span>
      ))}
      {tasks.length > MAX_DOTS && <span className="text-[10px] leading-none text-muted-foreground">+</span>}
    </span>
  )
}

export function MonthGrid({ anchor, selected, today, byDay, timeZone, onSelect, onOpen }: MonthGridProps) {
  const { days, month } = buildMonthGrid(anchor)
  const weeks = Array.from({ length: days.length / 7 }, (_, week) => days.slice(week * 7, week * 7 + 7))

  return (
    <table aria-label="Kalender bulanan" className="w-full table-fixed border-collapse overflow-hidden rounded-xl border">
      <thead>
        <tr>
          {WEEKDAY_SHORT.map((short, index) => (
            <th key={short} scope="col" abbr={WEEKDAY_NAMES[index]} className="border-b bg-muted/50 px-1 py-1.5 text-xs font-medium text-muted-foreground">
              {short}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {weeks.map((week) => (
          <tr key={week[0]}>
            {week.map((date) => {
              const tasks = byDay.get(date) ?? []
              const outside = monthKey(date) !== month
              const isToday = date === today
              return (
                <td
                  key={date}
                  data-outside-month={outside ? "true" : undefined}
                  className={cn("h-16 border p-0.5 align-top sm:h-28 sm:p-1", outside && "bg-muted/40 text-muted-foreground")}
                >
                  <div className="flex h-full min-w-0 flex-col gap-0.5">
                    <button
                      type="button"
                      aria-label={`${longDate(date)}, ${tasksLabel(tasks.length)}`}
                      aria-current={isToday ? "date" : undefined}
                      aria-pressed={date === selected}
                      onClick={() => onSelect(date)}
                      className={cn(
                        "flex w-full flex-col items-center gap-0.5 rounded-md py-0.5 text-sm outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring sm:items-start sm:px-1",
                        date === selected && "ring-2 ring-primary",
                        outside && "opacity-60",
                      )}
                    >
                      <span
                        className={cn(
                          "flex size-6 items-center justify-center rounded-full text-xs font-medium",
                          isToday && "bg-primary text-primary-foreground",
                        )}
                      >
                        {dayOfMonth(date)}
                      </span>
                      <Dots tasks={tasks} />
                    </button>
                    <div className="hidden min-w-0 flex-col gap-0.5 sm:flex">
                      {tasks.slice(0, MAX_CHIPS).map((task) => (
                        <TaskChip key={task.id} task={task} timeZone={timeZone} onOpen={onOpen} />
                      ))}
                      {tasks.length > MAX_CHIPS && (
                        <button
                          type="button"
                          onClick={() => onSelect(date)}
                          className="rounded-sm px-1 text-left text-xs text-muted-foreground outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring"
                        >
                          +{tasks.length - MAX_CHIPS} lagi
                        </button>
                      )}
                    </div>
                  </div>
                </td>
              )
            })}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
