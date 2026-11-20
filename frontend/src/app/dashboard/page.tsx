import type { Metadata } from "next"

import { DashboardView } from "@/features/dashboard/DashboardView"

export const metadata: Metadata = { title: "Dashboard | StudentTask" }

export default function DashboardPage() {
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold tracking-tight">Dashboard</h1>
      <DashboardView />
    </section>
  )
}
