import { describe, expect, it } from "vitest"

import {
  addDays,
  addMonths,
  buildMonthGrid,
  dayOfWeekMondayFirst,
  isValidDate,
  parseView,
  shiftAnchor,
  titleFor,
  visibleRange,
} from "./calendarDates"

describe("calendarDates", () => {
  it("month_grid_starts_on_monday_and_contains_whole_weeks", () => {
    for (const month of ["2026-02-10", "2026-03-10", "2026-11-21", "2026-12-01", "2027-02-28", "2028-02-29", "2026-06-30"]) {
      const grid = buildMonthGrid(month)
      expect(dayOfWeekMondayFirst(grid.days[0])).toBe(0)
      expect(grid.days.length % 7).toBe(0)
      expect(grid.days.length).toBeGreaterThanOrEqual(28)
      expect(grid.days.length).toBeLessThanOrEqual(42)
      expect(grid.days.filter((day) => day.startsWith(month.slice(0, 7)))).toHaveLength(
        new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)).getUTCDate(),
      )
    }
  })

  it("february_2027_fits_exactly_four_weeks_and_november_2026_needs_six", () => {
    expect(buildMonthGrid("2027-02-10").days).toHaveLength(28)
    expect(buildMonthGrid("2026-11-21").days).toHaveLength(42)
    expect(buildMonthGrid("2026-11-21").days[0]).toBe("2026-10-26")
  })

  it("range_for_a_month_never_exceeds_62_days_in_any_zone", () => {
    const zones = ["Asia/Jakarta", "America/New_York", "Pacific/Auckland", "Australia/Lord_Howe", "Europe/London"]
    for (const timeZone of zones) {
      for (let month = 1; month <= 12; month++) {
        const range = visibleRange("month", `2026-${String(month).padStart(2, "0")}-15`, timeZone)
        const spanMs = Date.parse(range.to) - Date.parse(range.from)
        expect(spanMs, `${timeZone} ${month}`).toBeLessThanOrEqual(62 * 24 * 3_600_000)
        expect(spanMs).toBeGreaterThan(0)
      }
    }
  })

  it("range_boundaries_are_local_midnights_with_offset", () => {
    expect(visibleRange("day", "2026-11-21", "Asia/Jakarta")).toMatchObject({
      from: "2026-11-21T00:00:00+07:00",
      to: "2026-11-22T00:00:00+07:00",
    })
    expect(visibleRange("week", "2026-11-21", "Asia/Jakarta")).toMatchObject({
      from: "2026-11-16T00:00:00+07:00",
      to: "2026-11-23T00:00:00+07:00",
    })
    // DST ends on 2026-11-01 in New York: the month grid starts in EDT and ends in EST.
    expect(visibleRange("month", "2026-11-21", "America/New_York")).toMatchObject({
      from: "2026-10-26T00:00:00-04:00",
      to: "2026-12-07T00:00:00-05:00",
    })
  })

  it("adds_days_and_months_with_clamping", () => {
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01")
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28")
    expect(addMonths("2026-01-31", 1)).toBe("2026-02-28")
    expect(addMonths("2026-12-15", 1)).toBe("2027-01-15")
    expect(addMonths("2026-01-15", -1)).toBe("2025-12-15")
    expect(shiftAnchor("week", "2026-11-21", 1)).toBe("2026-11-28")
    expect(shiftAnchor("day", "2026-11-21", -1)).toBe("2026-11-20")
  })

  it("validates_dates_and_views", () => {
    expect(isValidDate("2026-02-29")).toBe(false)
    expect(isValidDate("2028-02-29")).toBe(true)
    expect(isValidDate("2026-13-01")).toBe(false)
    expect(isValidDate("26-1-1")).toBe(false)
    expect(parseView("week")).toBe("week")
    expect(parseView("year")).toBe("month")
    expect(parseView(null)).toBe("month")
  })

  it("titles_are_indonesian", () => {
    expect(titleFor("month", "2026-11-21")).toBe("November 2026")
    expect(titleFor("week", "2026-11-21")).toBe("16–22 November 2026")
    expect(titleFor("week", "2026-11-30")).toBe("30 Nov – 6 Des 2026")
    expect(titleFor("week", "2026-12-31")).toBe("28 Des 2026 – 3 Jan 2027")
    expect(titleFor("day", "2026-11-21")).toBe("Sabtu, 21 November 2026")
  })
})
