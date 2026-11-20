import type { Metadata } from "next"
import Link from "next/link"
import { Suspense } from "react"
import { Plus } from "lucide-react"

import { buttonVariants } from "@/components/ui/button"
import { TaskListView } from "@/features/tasks/TaskListView"

export const metadata: Metadata = { title: "My Tasks | StudentTask" }

export default function TasksPage() {
  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold tracking-tight">My Tasks</h1>
        <Link href="/tasks/new" className={buttonVariants()}>
          <Plus aria-hidden="true" />
          Tugas baru
        </Link>
      </div>
      {/* useSearchParams needs a Suspense boundary with cacheComponents. */}
      <Suspense>
        <TaskListView />
      </Suspense>
    </section>
  )
}
