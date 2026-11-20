import { describe, expect, it } from "vitest"

import {
  DEFAULT_TIME_ZONE,
  describeDeadline,
  isoToZonedParts,
  resolveTimeZone,
  startOfZonedDayIso,
  zonedDateTimeToIso,
} from "./datetime"

describe("zonedDateTimeToIso", () => {
  it("uses_plus_07_for_asia_jakarta", () => {
    expect(zonedDateTimeToIso("2026-12-01", "23:59", "Asia/Jakarta")).toBe("2026-12-01T23:59:00+07:00")
  })

  it("uses_minus_05_in_new_york_winter_and_minus_04_in_summer", () => {
    expect(zonedDateTimeToIso("2026-12-01", "09:00", "America/New_York")).toBe("2026-12-01T09:00:00-05:00")
    expect(zonedDateTimeToIso("2026-07-01", "09:00", "America/New_York")).toBe("2026-07-01T09:00:00-04:00")
  })

  it("supports_half_hour_offsets", () => {
    expect(zonedDateTimeToIso("2026-12-01", "10:00", "Asia/Kolkata")).toBe("2026-12-01T10:00:00+05:30")
  })

  it("does_not_depend_on_the_browser_zone_and_denotes_the_right_instant", () => {
    const iso = zonedDateTimeToIso("2026-12-01", "23:59", "Asia/Jakarta")
    expect(new Date(iso).toISOString()).toBe("2026-12-01T16:59:00.000Z")
  })

  it("normalises_a_nonexistent_dst_gap_time_to_a_real_instant", () => {
    // 2026-03-08 02:30 does not exist in New York (clocks jump 02:00 -> 03:00).
    const iso = zonedDateTimeToIso("2026-03-08", "02:30", "America/New_York")
    expect(iso).toBe("2026-03-08T03:30:00-04:00")
  })
})

describe("isoToZonedParts", () => {
  it("converts_utc_back_to_local_date_and_time", () => {
    expect(isoToZonedParts("2026-12-01T16:59:00Z", "Asia/Jakarta")).toEqual({ date: "2026-12-01", time: "23:59" })
    expect(isoToZonedParts("2026-12-01T20:00:00Z", "Asia/Jakarta")).toEqual({ date: "2026-12-02", time: "03:00" })
    expect(isoToZonedParts("2026-12-01T05:00:00Z", "America/New_York")).toEqual({ date: "2026-12-01", time: "00:00" })
  })

  it("round_trips", () => {
    const iso = zonedDateTimeToIso("2026-07-15", "00:00", "America/New_York")
    expect(isoToZonedParts(iso, "America/New_York")).toEqual({ date: "2026-07-15", time: "00:00" })
  })
})

describe("startOfZonedDayIso", () => {
  it("returns_local_midnight_with_offset_and_supports_day_shift", () => {
    expect(startOfZonedDayIso("2026-12-01", "Asia/Jakarta")).toBe("2026-12-01T00:00:00+07:00")
    expect(startOfZonedDayIso("2026-12-31", "Asia/Jakarta", 1)).toBe("2027-01-01T00:00:00+07:00")
  })
})

describe("resolveTimeZone", () => {
  it("falls_back_to_asia_jakarta_for_missing_or_invalid_zones", () => {
    expect(DEFAULT_TIME_ZONE).toBe("Asia/Jakarta")
    expect(resolveTimeZone(undefined)).toBe("Asia/Jakarta")
    expect(resolveTimeZone("Bukan/Zona")).toBe("Asia/Jakarta")
    expect(resolveTimeZone("America/New_York")).toBe("America/New_York")
  })
})

describe("describeDeadline", () => {
  const now = Date.parse("2026-12-01T10:00:00Z")

  it("marks_open_past_deadlines_as_overdue", () => {
    expect(describeDeadline("2026-11-29T10:00:00Z", "TODO", now)).toEqual({ text: "Terlambat 2 hari", overdue: true })
    expect(describeDeadline("2026-12-01T09:00:00Z", "IN_PROGRESS", now)).toEqual({ text: "Terlambat 1 jam", overdue: true })
  })

  it("does_not_mark_completed_tasks_overdue", () => {
    expect(describeDeadline("2026-11-29T10:00:00Z", "COMPLETED", now)).toEqual({ text: "", overdue: false })
  })

  it("describes_upcoming_deadlines", () => {
    expect(describeDeadline("2026-12-01T10:30:00Z", "TODO", now)).toEqual({ text: "30 menit lagi", overdue: false })
    expect(describeDeadline("2026-12-01T15:00:00Z", "TODO", now)).toEqual({ text: "5 jam lagi", overdue: false })
    expect(describeDeadline("2026-12-04T10:00:00Z", "TODO", now)).toEqual({ text: "3 hari lagi", overdue: false })
  })
})
