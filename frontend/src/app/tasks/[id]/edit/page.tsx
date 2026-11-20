import type { Metadata } from "next"
import { Suspense } from "react"

import { TaskEdit } from "@/features/tasks/TaskDetail"

export const metadata: Metadata = { title: "Ubah tugas | StudentTask" }

export default function EditTaskPage() {
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold tracking-tight">Ubah tugas</h1>
      <Suspense>
        <TaskEdit />
      </Suspense>
    </section>
  )
}
