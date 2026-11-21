import type { Metadata } from "next"

import { SettingsView } from "@/features/settings/SettingsView"

export const metadata: Metadata = { title: "Settings | StudentTask" }

export default function SettingsPage() {
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold tracking-tight">Settings</h1>
      <SettingsView />
    </section>
  )
}
