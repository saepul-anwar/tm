import { describe, expect, it } from "vitest"

import { DEFAULT_FILTERS, parseTaskFilters, toApiQuery, toSearchString } from "./filters"

describe("parseTaskFilters", () => {
  it("returns_defaults_for_an_empty_query", () => {
    expect(parseTaskFilters(new URLSearchParams(), DEFAULT_FILTERS)).toEqual(DEFAULT_FILTERS)
  })

  it("reads_valid_values_and_ignores_invalid_ones", () => {
    const filters = parseTaskFilters(
      new URLSearchParams("q=lab&status=DONE&priority=URGENT&sort=nope&dir=up&page=-2&size=500"),
      DEFAULT_FILTERS,
    )
    expect(filters).toMatchObject({ q: "lab", status: "", priority: "URGENT", sort: "deadline", dir: "asc", page: 0, size: 10 })
  })
})

describe("toSearchString", () => {
  it("omits_defaults", () => {
    expect(toSearchString(DEFAULT_FILTERS, DEFAULT_FILTERS)).toBe("")
    expect(toSearchString({ ...DEFAULT_FILTERS, status: "TODO", page: 2 }, DEFAULT_FILTERS)).toBe("status=TODO&page=2")
  })
})

describe("toApiQuery", () => {
  it("encodes_plus_in_offsets_as_percent_2B_and_makes_deadline_to_exclusive", () => {
    const query = toApiQuery(
      { ...DEFAULT_FILTERS, deadlineFrom: "2026-12-01", deadlineTo: "2026-12-02" },
      "Asia/Jakarta",
    )
    expect(query).toContain("deadlineFrom=2026-12-01T00%3A00%3A00%2B07%3A00")
    expect(query).toContain("deadlineTo=2026-12-03T00%3A00%3A00%2B07%3A00")
    expect(query).not.toContain("+07")
  })

  it("sends_paging_and_sorting_and_skips_empty_filters", () => {
    expect(toApiQuery({ ...DEFAULT_FILTERS, q: "a b", sort: "priority", dir: "desc", page: 1 }, "Asia/Jakarta")).toBe(
      "q=a+b&sort=priority&dir=desc&page=1&size=10",
    )
  })
})
