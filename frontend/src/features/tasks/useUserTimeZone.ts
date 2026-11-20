"use client"

import { useAuth } from "@/lib/auth/AuthProvider"

import { resolveTimeZone } from "./datetime"

/** The profile zone (users.timezone), falling back to Asia/Jakarta. Never the browser zone. */
export function useUserTimeZone(): string {
  const { user } = useAuth()
  return resolveTimeZone(user?.timezone)
}
