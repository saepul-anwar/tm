import type { DashboardCounts } from "./types"

type ProgressCardProps = { completionPercent: number; counts: DashboardCounts }

export function ProgressCard({ completionPercent, counts }: ProgressCardProps) {
  const percent = Math.min(100, Math.max(0, completionPercent))
  return (
    <section aria-labelledby="progress-heading" className="flex flex-col gap-3 rounded-xl border p-4">
      <div className="flex items-baseline justify-between gap-2">
        <h2 id="progress-heading" className="font-medium">
          Progres
        </h2>
        <span className="text-2xl font-semibold tabular-nums">{percent}%</span>
      </div>
      <div
        role="progressbar"
        aria-label="Progres penyelesaian tugas"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className="h-2.5 w-full overflow-hidden rounded-full bg-muted"
      >
        <div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${percent}%` }} />
      </div>
      <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
        <li>{counts.todo} belum dikerjakan</li>
        <li>{counts.inProgress} sedang dikerjakan</li>
        <li>{counts.completed} selesai</li>
      </ul>
    </section>
  )
}
