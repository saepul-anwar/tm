"use client"

import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { useCallback, useMemo } from "react"

import { parseTaskFilters, toSearchString, type TaskListFilters } from "./filters"

/** Filter state lives in the URL query string, so it survives reloads and can be shared. */
export function useTaskFilterState(defaults: TaskListFilters) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const search = searchParams.toString()

  const filters = useMemo(() => parseTaskFilters(new URLSearchParams(search), defaults), [search, defaults])

  const navigate = useCallback(
    (next: TaskListFilters) => {
      const query = toSearchString(next, defaults)
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
    },
    [router, pathname, defaults],
  )

  const update = useCallback(
    (patch: Partial<TaskListFilters>) => {
      // Any change other than paging itself goes back to the first page.
      navigate({ ...filters, page: 0, ...patch })
    },
    [filters, navigate],
  )

  const reset = useCallback(() => navigate(defaults), [navigate, defaults])

  return { filters, update, reset }
}
