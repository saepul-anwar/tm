"use client"

import { useState } from "react"

import { useApiQuery } from "@/features/tasks/useApiQuery"

import { fetchProfile } from "./settingsApi"
import type { Profile } from "./types"

/** Loads the profile; `update` swaps in the server's latest copy after a successful save. */
export function useProfile() {
  const query = useApiQuery("profile", fetchProfile)
  const [updated, setUpdated] = useState<Profile | null>(null)
  return {
    profile: updated ?? query.data,
    error: updated ? null : query.error,
    isLoading: !updated && query.isLoading,
    refetch: () => {
      setUpdated(null)
      query.refetch()
    },
    update: setUpdated,
  }
}
