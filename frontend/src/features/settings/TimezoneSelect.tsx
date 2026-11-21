"use client"

import { useMemo, useState } from "react"

import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Select } from "@/components/ui/select"
import { DEFAULT_TIME_ZONE } from "@/features/tasks/datetime"
import { describeApiError } from "@/lib/api-errors"
import { useAuth } from "@/lib/auth/AuthProvider"

import { updateProfile } from "./settingsApi"
import type { Profile } from "./types"

function listTimeZones(current: string): string[] {
  const supported = typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("timeZone") : []
  const zones = new Set<string>([DEFAULT_TIME_ZONE, ...supported])
  zones.add(current) // keep a saved zone selectable even if this browser does not list it
  return [DEFAULT_TIME_ZONE, ...[...zones].filter((zone) => zone !== DEFAULT_TIME_ZONE).sort()]
}

type TimezoneSelectProps = { timezone: string; onSaved: (profile: Profile) => void }

export function TimezoneSelect({ timezone, onSaved }: TimezoneSelectProps) {
  const { refreshUser } = useAuth()
  const zones = useMemo(() => listTimeZones(timezone), [timezone])
  const [selected, setSelected] = useState(timezone)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  async function save() {
    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      const next = await updateProfile({ timezone: selected })
      onSaved(next)
      setSaved(true)
      await refreshUser()
    } catch (caught) {
      setError(describeApiError(caught))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex max-w-md flex-col gap-3">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="timezone">Zona waktu</Label>
        <Select id="timezone" value={selected} onChange={(event) => setSelected(event.target.value)}>
          {zones.map((zone) => (
            <option key={zone} value={zone}>
              {zone}
            </option>
          ))}
        </Select>
        <p className="text-xs text-muted-foreground">
          Dipakai untuk menampilkan deadline dan pesan pengingat. Bukan zona waktu browser.
        </p>
      </div>
      {error && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      )}
      {saved && (
        <p role="status" className="text-sm text-muted-foreground">
          Zona waktu disimpan.
        </p>
      )}
      <div>
        <Button onClick={() => void save()} disabled={saving || selected === timezone}>
          {saving ? "Menyimpan..." : "Simpan zona waktu"}
        </Button>
      </div>
    </div>
  )
}
