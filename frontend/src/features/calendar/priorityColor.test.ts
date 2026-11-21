import { describe, expect, it } from "vitest"

import { PRIORITY_MARKS } from "./priorityColor"
import { PRIORITIES } from "@/features/tasks/types"

describe("priorityColor", () => {
  it("priority_colors_and_shapes_are_distinct", () => {
    const chips = PRIORITIES.map((priority) => PRIORITY_MARKS[priority].chip)
    const glyphs = PRIORITIES.map((priority) => PRIORITY_MARKS[priority].glyph)
    expect(new Set(chips).size).toBe(4)
    expect(new Set(glyphs).size).toBe(4)
  })
})
