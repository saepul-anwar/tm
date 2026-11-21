import { PRIORITY_BADGE_STYLES } from "@/features/tasks/PriorityBadge"
import type { Priority } from "@/features/tasks/types"

type PriorityMark = {
  /** Background + text, the same palette as PriorityBadge. */
  chip: string
  /** Left accent so a chip stays recognisable even with low contrast between backgrounds. */
  accent: string
  /** Text color only, for the compact mobile marker. */
  glyphColor: string
  /** Shape cue so priority never relies on color alone. */
  glyph: string
}

export const PRIORITY_MARKS: Record<Priority, PriorityMark> = {
  LOW: { chip: PRIORITY_BADGE_STYLES.LOW, accent: "border-l-emerald-500", glyphColor: "text-emerald-600 dark:text-emerald-400", glyph: "○" },
  MEDIUM: { chip: PRIORITY_BADGE_STYLES.MEDIUM, accent: "border-l-sky-500", glyphColor: "text-sky-600 dark:text-sky-400", glyph: "◇" },
  HIGH: { chip: PRIORITY_BADGE_STYLES.HIGH, accent: "border-l-orange-500", glyphColor: "text-orange-600 dark:text-orange-400", glyph: "△" },
  URGENT: { chip: PRIORITY_BADGE_STYLES.URGENT, accent: "border-l-red-500", glyphColor: "text-red-600 dark:text-red-400", glyph: "■" },
}
