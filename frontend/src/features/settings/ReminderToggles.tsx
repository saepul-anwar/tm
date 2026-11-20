"use client"

import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useApiQuery } from "@/features/tasks/useApiQuery"
import { describeApiError } from "@/lib/api-errors"
import { cn } from "@/lib/utils"

import { fetchReminderSettings, updateReminderSettings } from "./settingsApi"
import type { ReminderSettings } from "./types"

const OPTIONS: { key: keyof ReminderSettings; label: string }[] = [
  { key: "reminder24Hours", label: "24 jam sebelum deadline" },
  { key: "reminder6Hours", label: "6 jam sebelum deadline" },
  { key: "reminder1Hour", label: "1 jam sebelum deadline" },
  { key: "reminderAtDeadline", label: "Saat deadline" },
]

export function ReminderToggles() {
  const query = useApiQuery("reminder-settings", fetchReminderSettings)
  // Local copy only after the user toggled something; otherwise the loaded server value is shown.
  const [override, setOverride] = useState<ReminderSettings | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (query.error) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p role="alert" className="text-sm text-destructive">
          {describeApiError(query.error)}
        </p>
        <Button variant="outline" onClick={query.refetch}>
          Coba lagi
        </Button>
      </div>
    )
  }

  const settings = override ?? query.data
  if (!settings) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true" aria-label="Memuat pengaturan pengingat">
        {OPTIONS.map((option) => (
          <Skeleton key={option.key} className="h-6 w-full max-w-xs" />
        ))}
      </div>
    )
  }

  async function toggle(key: keyof ReminderSettings) {
    if (!settings) return
    const previous = settings
    const next = { ...previous, [key]: !previous[key] }
    setOverride(next)
    setError(null)
    setSaving(true)
    try {
      setOverride(await updateReminderSettings(next))
    } catch (caught) {
      setOverride(previous)
      setError(describeApiError(caught))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-3">
        {OPTIONS.map(({ key, label }) => (
          <li key={key}>
            <label className="flex w-fit cursor-pointer items-center gap-3 text-sm">
              <input
                type="checkbox"
                role="switch"
                checked={settings[key]}
                disabled={saving}
                onChange={() => void toggle(key)}
                className={cn(
                  "relative h-5 w-9 shrink-0 cursor-pointer appearance-none rounded-full bg-input transition-colors outline-none",
                  "checked:bg-primary focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-60",
                  "after:absolute after:top-0.5 after:left-0.5 after:size-4 after:rounded-full after:bg-background after:shadow after:transition-transform after:content-['']",
                  "checked:after:translate-x-4",
                )}
              />
              {label}
            </label>
          </li>
        ))}
      </ul>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
