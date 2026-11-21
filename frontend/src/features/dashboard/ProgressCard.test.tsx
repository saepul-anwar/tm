import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { ProgressCard } from "./ProgressCard"

const counts = { total: 7, todo: 3, inProgress: 2, completed: 2, overdue: 0, upcoming: 0 }

describe("ProgressCard", () => {
  it("progressbar_has_correct_aria_valuenow", () => {
    render(<ProgressCard completionPercent={29} counts={counts} />)

    const bar = screen.getByRole("progressbar", { name: "Progres penyelesaian tugas" })
    expect(bar).toHaveAttribute("aria-valuenow", "29")
    expect(bar).toHaveAttribute("aria-valuemin", "0")
    expect(bar).toHaveAttribute("aria-valuemax", "100")
    expect(screen.getByText("29%")).toBeInTheDocument()
  })

  it("shows_status_breakdown", () => {
    render(<ProgressCard completionPercent={29} counts={counts} />)

    expect(screen.getByText("3 belum dikerjakan")).toBeInTheDocument()
    expect(screen.getByText("2 sedang dikerjakan")).toBeInTheDocument()
    expect(screen.getByText("2 selesai")).toBeInTheDocument()
  })
})
