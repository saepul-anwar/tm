"use client"

import type { ReactNode } from "react"

import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { resolveTimeZone } from "@/features/tasks/datetime"
import { describeApiError } from "@/lib/api-errors"

import { PasswordForm } from "./PasswordForm"
import { ProfileForm } from "./ProfileForm"
import { ReminderToggles } from "./ReminderToggles"
import { TelegramCard } from "./TelegramCard"
import { TimezoneSelect } from "./TimezoneSelect"
import { useProfile } from "./useProfile"

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-4 rounded-xl border bg-card p-4 md:p-6">
      <h2 id={id} className="text-base font-semibold">
        {title}
      </h2>
      {children}
    </section>
  )
}

function SectionError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3">
      <p role="alert" className="text-sm text-destructive">
        {describeApiError(error)}
      </p>
      <Button variant="outline" onClick={onRetry}>
        Coba lagi
      </Button>
    </div>
  )
}

function SectionSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-busy="true" aria-label="Memuat">
      <Skeleton className="h-9 w-full max-w-md" />
      <Skeleton className="h-9 w-full max-w-md" />
    </div>
  )
}

export function SettingsView() {
  const { profile, error, isLoading, refetch, update } = useProfile()

  // Sections that depend on the profile share one load; each shows its own error/retry.
  function withProfile(render: (loaded: NonNullable<typeof profile>) => ReactNode): ReactNode {
    if (error) return <SectionError error={error} onRetry={refetch} />
    if (isLoading || !profile) return <SectionSkeleton />
    return render(profile)
  }

  return (
    <div className="flex flex-col gap-4">
      <Section id="settings-profile" title="Profil">
        {withProfile((loaded) => (
          <ProfileForm profile={loaded} onSaved={update} />
        ))}
      </Section>
      <Section id="settings-password" title="Ganti password">
        <PasswordForm />
      </Section>
      <Section id="settings-timezone" title="Zona waktu">
        {withProfile((loaded) => (
          <TimezoneSelect key={loaded.timezone} timezone={loaded.timezone} onSaved={update} />
        ))}
      </Section>
      <Section id="settings-telegram" title="Telegram">
        {withProfile((loaded) => (
          <TelegramCard initial={loaded.telegram} timeZone={resolveTimeZone(loaded.timezone)} />
        ))}
      </Section>
      <Section id="settings-reminders" title="Pengingat">
        <ReminderToggles />
      </Section>
    </div>
  )
}
