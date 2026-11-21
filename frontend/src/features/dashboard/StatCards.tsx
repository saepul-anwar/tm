import { cn } from "@/lib/utils"

import type { DashboardCounts } from "./types"

type Stat = { key: keyof DashboardCounts; label: string; danger?: boolean }

const STATS: Stat[] = [
  { key: "total", label: "Total" },
  { key: "todo", label: "Belum dikerjakan" },
  { key: "inProgress", label: "Sedang dikerjakan" },
  { key: "completed", label: "Selesai" },
  { key: "overdue", label: "Terlambat", danger: true },
  { key: "upcoming", label: "7 hari ke depan" },
]

export function StatCards({ counts }: { counts: DashboardCounts }) {
  return (
    <ul aria-label="Ringkasan tugas" className="grid grid-cols-1 gap-3 min-[480px]:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      {STATS.map(({ key, label, danger }) => {
        const isDanger = danger === true && counts[key] > 0
        return (
          <li
            key={key}
            data-danger={isDanger ? "true" : undefined}
            className={cn(
              "flex flex-col gap-1 rounded-xl border p-4",
              isDanger && "border-destructive/40 bg-destructive/5",
            )}
          >
            <span className="text-sm text-muted-foreground">{label}</span>
            <span className={cn("text-2xl font-semibold tabular-nums", isDanger && "text-destructive")}>{counts[key]}</span>
          </li>
        )
      })}
    </ul>
  )
}
