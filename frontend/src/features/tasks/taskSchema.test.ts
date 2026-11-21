import { describe, expect, it } from "vitest"

import { taskSchema, toTaskPayload, type TaskFormValues } from "./taskSchema"

const valid: TaskFormValues = {
  title: "Laporan praktikum",
  description: "",
  subject: "",
  deadlineDate: "2026-12-01",
  deadlineTime: "23:59",
  priority: "MEDIUM",
  status: "TODO",
}

const issuesFor = (input: Partial<TaskFormValues>) => {
  const result = taskSchema.safeParse({ ...valid, ...input })
  const first: Record<string, string> = {}
  if (!result.success) result.error.issues.forEach((i) => (first[i.path.join(".")] ??= i.message))
  return first
}

describe("taskSchema", () => {
  it("taskSchema_requires_title_date_and_time", () => {
    const issues = issuesFor({ title: "   ", deadlineDate: "", deadlineTime: "" })
    expect(issues.title).toBe("Judul wajib diisi.")
    expect(issues.deadlineDate).toBe("Tanggal deadline wajib diisi.")
    expect(issues.deadlineTime).toBe("Jam deadline wajib diisi.")
  })

  it("accepts_a_valid_task_and_trims_the_title", () => {
    const result = taskSchema.safeParse({ ...valid, title: "  Judul  " })
    expect(result.success && result.data.title).toBe("Judul")
  })

  it("enforces_length_limits", () => {
    expect(issuesFor({ title: "a".repeat(201) }).title).toBe("Judul maksimal 200 karakter.")
    expect(issuesFor({ title: "a".repeat(200) }).title).toBeUndefined()
    expect(issuesFor({ description: "a".repeat(5001) }).description).toBe("Deskripsi maksimal 5000 karakter.")
    expect(issuesFor({ subject: "a".repeat(101) }).subject).toBe("Mata kuliah maksimal 100 karakter.")
  })

  it("rejects_impossible_dates_and_times", () => {
    expect(issuesFor({ deadlineDate: "2026-02-31" }).deadlineDate).toBe("Tanggal deadline tidak valid.")
    expect(issuesFor({ deadlineTime: "25:00" }).deadlineTime).toBe("Jam deadline tidak valid.")
  })

  it("rejects_unknown_priority_and_status", () => {
    expect(issuesFor({ priority: "NOPE" as never }).priority).toBeDefined()
    expect(issuesFor({ status: "NOPE" as never }).status).toBeDefined()
  })
})

describe("toTaskPayload", () => {
  it("sends_empty_description_and_subject_as_null_and_deadline_with_offset", () => {
    expect(toTaskPayload(valid, "Asia/Jakarta")).toEqual({
      title: "Laporan praktikum",
      description: null,
      subject: null,
      deadline: "2026-12-01T23:59:00+07:00",
      priority: "MEDIUM",
      status: "TODO",
    })
  })

  it("includes_version_only_when_given", () => {
    expect(toTaskPayload(valid, "Asia/Jakarta", 7)).toMatchObject({ version: 7 })
    expect(toTaskPayload(valid, "Asia/Jakarta")).not.toHaveProperty("version")
  })
})
