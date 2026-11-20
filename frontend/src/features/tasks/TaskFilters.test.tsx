import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { TaskFilters } from "./TaskFilters"
import { DEFAULT_FILTERS } from "./filters"
import { useTaskFilterState } from "./useTaskFilterState"

const mocks = vi.hoisted(() => ({ search: "", replace: vi.fn() }))

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace, push: vi.fn() }),
  usePathname: () => "/tasks",
  useSearchParams: () => new URLSearchParams(mocks.search),
}))

function Harness() {
  const { filters, update, reset } = useTaskFilterState(DEFAULT_FILTERS)
  return <TaskFilters filters={filters} subjects={["Biologi", "Fisika"]} onChange={update} onReset={reset} />
}

beforeEach(() => {
  mocks.search = ""
  mocks.replace.mockReset()
})

describe("TaskFilters", () => {
  it("TaskFilters_updates_query_params", async () => {
    const actor = userEvent.setup()
    render(<Harness />)

    await actor.selectOptions(screen.getByLabelText("Status"), "COMPLETED")
    expect(mocks.replace).toHaveBeenLastCalledWith("/tasks?status=COMPLETED", { scroll: false })

    await actor.selectOptions(screen.getByLabelText("Prioritas"), "URGENT")
    expect(mocks.replace).toHaveBeenLastCalledWith("/tasks?priority=URGENT", { scroll: false })

    await actor.selectOptions(screen.getByLabelText("Mata kuliah"), "Fisika")
    expect(mocks.replace).toHaveBeenLastCalledWith("/tasks?subject=Fisika", { scroll: false })

    await actor.selectOptions(screen.getByLabelText("Urutkan"), "priority")
    expect(mocks.replace).toHaveBeenLastCalledWith("/tasks?sort=priority", { scroll: false })
  })

  it("debounces_the_search_text_before_updating_the_url", async () => {
    const actor = userEvent.setup()
    render(<Harness />)

    await actor.type(screen.getByLabelText("Cari judul"), "lab")
    expect(mocks.replace).not.toHaveBeenCalled()
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledTimes(1), { timeout: 2000 })
    expect(mocks.replace).toHaveBeenCalledWith("/tasks?q=lab", { scroll: false })
  })

  it("changing_a_filter_resets_the_page", async () => {
    const actor = userEvent.setup()
    mocks.search = "page=3&status=TODO"
    render(<Harness />)

    await actor.selectOptions(screen.getByLabelText("Prioritas"), "LOW")
    expect(mocks.replace).toHaveBeenLastCalledWith("/tasks?status=TODO&priority=LOW", { scroll: false })
  })

  it("reads_initial_values_from_the_url_and_reset_clears_them", async () => {
    const actor = userEvent.setup()
    mocks.search = "status=IN_PROGRESS&q=abc"
    render(<Harness />)

    expect(screen.getByLabelText("Status")).toHaveValue("IN_PROGRESS")
    expect(screen.getByLabelText("Cari judul")).toHaveValue("abc")
    await actor.click(screen.getByRole("button", { name: "Reset filter" }))
    expect(mocks.replace).toHaveBeenLastCalledWith("/tasks", { scroll: false })
  })
})
