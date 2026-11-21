"use client"

import { useSearchParams } from "next/navigation"

const NOTICES: Record<string, string> = {
  "password-changed": "Kata sandi berhasil diganti. Silakan masuk kembali dengan kata sandi baru.",
}

/** Shows a fixed, allow-listed message for `?notice=<key>`; the query value is never rendered. */
export function LoginNotice() {
  const key = useSearchParams().get("notice")
  const text = key ? NOTICES[key] : undefined
  if (!text) return null
  return (
    <p role="status" className="rounded-lg bg-muted px-3 py-2 text-sm">
      {text}
    </p>
  )
}
