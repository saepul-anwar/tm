import { cn } from "@/lib/utils"

import { longDate } from "./calendarDates"
import { TaskRow } from "./TaskButtons"
import type { CalendarTask } from "./types"

type DayAgendaProps = {
  date: string
  tasks: CalendarTask[]
  timeZone: string
  onOpen: (task: CalendarTask) => void
  className?: string
}

/** Tasks of one day in time order. Used as the month side panel and as the whole day view. */
export function DayAgenda({ date, tasks, timeZone, onOpen, className }: DayAgendaProps) {
  return (
    <section aria-label={`Tugas ${longDate(date)}`} className={cn("flex min-w-0 flex-col gap-3 rounded-xl border p-4", className)}>
      <h3 className="font-medium">{longDate(date)}</h3>
      {tasks.length === 0 ? (
        <p className="text-sm text-muted-foreground">Tidak ada tugas pada tanggal ini.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {tasks.map((task) => (
            <li key={task.id}>
              <TaskRow task={task} timeZone={timeZone} onOpen={onOpen} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
