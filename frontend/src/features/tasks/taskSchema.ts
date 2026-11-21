import { z } from "zod"

import { zonedDateTimeToIso } from "./datetime"
import { PRIORITIES, STATUSES, type TaskPayload } from "./types"

// Mirrors the backend TaskRequest limits.
export const TITLE_MAX = 200
export const DESCRIPTION_MAX = 5000
export const SUBJECT_MAX = 100

const isRealDate = (value: string) => {
  const [year, month, day] = value.split("-").map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

export const taskSchema = z.object({
  title: z.string().trim().min(1, "Judul wajib diisi.").max(TITLE_MAX, `Judul maksimal ${TITLE_MAX} karakter.`),
  description: z.string().max(DESCRIPTION_MAX, `Deskripsi maksimal ${DESCRIPTION_MAX} karakter.`),
  subject: z.string().trim().max(SUBJECT_MAX, `Mata kuliah maksimal ${SUBJECT_MAX} karakter.`),
  deadlineDate: z
    .string()
    .min(1, "Tanggal deadline wajib diisi.")
    .regex(/^\d{4}-\d{2}-\d{2}$/, "Tanggal deadline tidak valid.")
    .refine(isRealDate, "Tanggal deadline tidak valid."),
  deadlineTime: z
    .string()
    .min(1, "Jam deadline wajib diisi.")
    .regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Jam deadline tidak valid."),
  priority: z.enum(PRIORITIES, "Prioritas tidak valid."),
  status: z.enum(STATUSES, "Status tidak valid."),
})

export type TaskFormValues = z.infer<typeof taskSchema>

/** Builds the API body. The deadline is read in the user's profile zone, not the browser zone. */
export function toTaskPayload(values: TaskFormValues, timeZone: string, version?: number): TaskPayload {
  const description = values.description.trim()
  const subject = values.subject.trim()
  return {
    title: values.title.trim(),
    description: description === "" ? null : values.description,
    subject: subject === "" ? null : subject,
    deadline: zonedDateTimeToIso(values.deadlineDate, values.deadlineTime, timeZone),
    priority: values.priority,
    status: values.status,
    ...(version === undefined ? {} : { version }),
  }
}
