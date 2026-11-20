import type { Metadata } from "next"

import { TaskForm } from "@/features/tasks/TaskForm"

export const metadata: Metadata = { title: "Tugas baru | StudentTask" }

export default function NewTaskPage() {
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold tracking-tight">Tugas baru</h1>
      <TaskForm />
    </section>
  )
}
