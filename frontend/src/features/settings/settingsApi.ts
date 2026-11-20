import { apiFetch } from "@/lib/api"

import type { Profile, ProfileUpdate, ReminderSettings, TelegramConnectResponse } from "./types"

const jsonBody = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) })

export const fetchProfile = (): Promise<Profile> => apiFetch<Profile>("/api/profile")

export const updateProfile = (update: ProfileUpdate): Promise<Profile> =>
  apiFetch<Profile>("/api/profile", jsonBody("PUT", update))

export const fetchReminderSettings = (): Promise<ReminderSettings> =>
  apiFetch<ReminderSettings>("/api/reminders/settings")

export const updateReminderSettings = (settings: ReminderSettings): Promise<ReminderSettings> =>
  apiFetch<ReminderSettings>("/api/reminders/settings", jsonBody("PUT", settings))

export const connectTelegram = (): Promise<TelegramConnectResponse> =>
  apiFetch<TelegramConnectResponse>("/api/telegram/connect", { method: "POST" })

export const disconnectTelegram = (): Promise<void> =>
  apiFetch<void>("/api/telegram/disconnect", { method: "DELETE" })

export const sendTelegramTest = (): Promise<{ sent: boolean }> =>
  apiFetch<{ sent: boolean }>("/api/telegram/test", { method: "POST" })
