import { render, screen, within } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { makeTask } from "@/features/tasks/test-fixtures"

import { TaskMiniList } from "./TaskMiniList"

describe("TaskMiniList", () => {
  it("rows_link_to_task_detail_and_show_subject_priority_and_local_deadline", () => {
    render(
      <TaskMiniList
        title="Segera diselesaikan"
        tasks={[makeTask({ id: 42, title: "Laporan Bab 3", subject: "Kimia", priority: "URGENT", deadline: "2026-12-01T16:59:00Z" })]}
        timeZone="Asia/Jakarta"
        emptyText="Kosong"
      />,
    )

    const link = screen.getByRole("link", { name: "Laporan Bab 3" })
    expect(link).toHaveAttribute("href", "/tasks/42")
    const row = link.closest("li") as HTMLElement
    expect(within(row).getByText("Kimia")).toBeInTheDocument()
    expect(within(row).getByText("Mendesak")).toBeInTheDocument()
    // 16:59 UTC = 23:59 WIB
    expect(within(row).getByText("1 Des 2026, 23.59")).toBeInTheDocument()
  })

  it.each([
    ["OVERDUE", "Terlambat"],
    ["DUE_TODAY", "Hari ini"],
    ["DUE_TOMORROW", "Besok"],
    ["UPCOMING", "Mendatang"],
    ["LATER", "Nanti"],
  ])("category_chip_text_for_%s", (category, text) => {
    render(
      <TaskMiniList title="Daftar" tasks={[makeTask({ deadlineCategory: category })]} timeZone="Asia/Jakarta" emptyText="Kosong" />,
    )
    expect(screen.getByTestId("category-chip")).toHaveTextContent(text)
    expect(screen.getByTestId("category-chip")).toHaveAttribute("data-category", category)
  })

  it("shows_no_chip_without_category_and_empty_text_without_tasks", () => {
    const { rerender } = render(
      <TaskMiniList title="Daftar" tasks={[makeTask({ deadlineCategory: null })]} timeZone="Asia/Jakarta" emptyText="Kosong" />,
    )
    expect(screen.queryByTestId("category-chip")).not.toBeInTheDocument()

    rerender(<TaskMiniList title="Daftar" tasks={[]} timeZone="Asia/Jakarta" emptyText="Tidak ada tugas." />)
    expect(screen.getByText("Tidak ada tugas.")).toBeInTheDocument()
  })
})
