import type { Metadata } from "next"
import { Suspense } from "react"

import { CalendarView } from "@/features/calendar/CalendarView"

export const metadata: Metadata = { title: "Calendar | StudentTask" }

export default function CalendarPage() {
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold tracking-tight">Calendar</h1>
      {/* useSearchParams needs a Suspense boundary with cacheComponents. */}
      <Suspense>
        <CalendarView />
      </Suspense>
    </section>
  )
}
