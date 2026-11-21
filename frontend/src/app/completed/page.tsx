import type { Metadata } from "next"
import { Suspense } from "react"

import { TaskListView } from "@/features/tasks/TaskListView"

export const metadata: Metadata = { title: "Completed | StudentTask" }

export default function CompletedPage() {
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold tracking-tight">Tugas selesai</h1>
      <Suspense>
        <TaskListView variant="completed" />
      </Suspense>
    </section>
  )
}
