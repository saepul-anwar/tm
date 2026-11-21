"use client"

import { useCallback, useEffect, useState } from "react"

type Settled<T> = { requestKey: string; data: T | null; error: unknown }

export type QueryState<T> = {
  data: T | null
  error: unknown
  isLoading: boolean
  refetch: () => void
}

/**
 * Minimal fetch-on-change hook. `key` identifies the request; when it changes (or
 * `refetch` is called) a new request starts and older responses are ignored. Previous
 * data is kept while loading so lists do not flash empty.
 */
export function useApiQuery<T>(key: string, fetcher: () => Promise<T>, enabled = true): QueryState<T> {
  const [nonce, setNonce] = useState(0)
  const [settled, setSettled] = useState<Settled<T> | null>(null)
  const requestKey = `${key}#${nonce}`

  useEffect(() => {
    if (!enabled) return
    let active = true
    fetcher().then(
      (data) => active && setSettled({ requestKey, data, error: null }),
      (error: unknown) => active && setSettled({ requestKey, data: null, error }),
    )
    return () => {
      active = false
    }
    // `fetcher` is intentionally excluded: it is recreated on every render and `key` describes it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestKey, enabled])

  const refetch = useCallback(() => setNonce((value) => value + 1), [])
  const isCurrent = settled?.requestKey === requestKey

  return {
    data: settled?.data ?? null,
    error: isCurrent ? settled.error : null,
    isLoading: !enabled || !isCurrent,
    refetch,
  }
}
