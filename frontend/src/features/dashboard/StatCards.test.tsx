import { render, screen, within } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { StatCards } from "./StatCards"
import type { DashboardCounts } from "./types"

const counts: DashboardCounts = { total: 7, todo: 3, inProgress: 2, completed: 2, overdue: 2, upcoming: 4 }

function card(label: string) {
  return within(screen.getByRole("list", { name: "Ringkasan tugas" }))
    .getByText(label)
    .closest("li") as HTMLElement
}

describe("StatCards", () => {
  it("StatCards_render_six_counts", () => {
    render(<StatCards counts={counts} />)

    expect(screen.getAllByRole("listitem")).toHaveLength(6)
    expect(card("Total")).toHaveTextContent("7")
    expect(card("Belum dikerjakan")).toHaveTextContent("3")
    expect(card("Sedang dikerjakan")).toHaveTextContent("2")
    expect(card("Selesai")).toHaveTextContent("2")
    expect(card("Terlambat")).toHaveTextContent("2")
    expect(card("7 hari ke depan")).toHaveTextContent("4")
  })

  it("uses_danger_styling_for_overdue_only_when_greater_than_zero", () => {
    const { rerender } = render(<StatCards counts={counts} />)
    expect(card("Terlambat")).toHaveAttribute("data-danger", "true")
    expect(card("Total")).not.toHaveAttribute("data-danger")

    rerender(<StatCards counts={{ ...counts, overdue: 0 }} />)
    expect(card("Terlambat")).not.toHaveAttribute("data-danger")
  })
})
