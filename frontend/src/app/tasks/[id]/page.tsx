import type { Metadata } from "next"
import { Suspense } from "react"

import { TaskDetail } from "@/features/tasks/TaskDetail"

export const metadata: Metadata = { title: "Detail tugas | StudentTask" }

export default function TaskDetailPage() {
  return (
    <section>
      <Suspense>
        <TaskDetail />
      </Suspense>
    </section>
  )
}
