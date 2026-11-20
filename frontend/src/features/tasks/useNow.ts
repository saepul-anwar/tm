"use client"

import { useEffect, useState } from "react"

const NOW_REFRESH_MS = 60_000

/** Current time in epoch ms, refreshed every minute so "overdue" hints stay current. */
export function useNow(): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), NOW_REFRESH_MS)
    return () => clearInterval(timer)
  }, [])
  return now
}
