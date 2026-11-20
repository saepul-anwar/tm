export type TelegramStatus = { connected: boolean; connectedAt: string | null }

export type Profile = {
  fullName: string
  email: string
  timezone: string
  telegram: TelegramStatus
}

/** Only the fields being changed are sent; omitted fields are left untouched by the backend. */
export type ProfileUpdate = Partial<{
  fullName: string
  email: string
  timezone: string
  currentPassword: string
  newPassword: string
}>

export type ReminderSettings = {
  reminder24Hours: boolean
  reminder6Hours: boolean
  reminder1Hour: boolean
  reminderAtDeadline: boolean
}

export type TelegramConnectResponse = { deepLink: string; expiresAt: string }
