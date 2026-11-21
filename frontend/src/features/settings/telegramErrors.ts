import { ApiError } from "@/lib/api"
import { describeApiError } from "@/lib/api-errors"

export const TELEGRAM_NOT_CONFIGURED_MESSAGE = "Bot Telegram belum dikonfigurasi di server."
export const TELEGRAM_NOT_CONNECTED_MESSAGE = "Telegram belum terhubung"
export const TELEGRAM_SEND_FAILED_MESSAGE = "Gagal mengirim ke Telegram"

/** Telegram endpoints reuse HTTP codes with action-specific meanings, so map them per action. */
export function describeTelegramError(error: unknown, action: "connect" | "test"): string {
  if (error instanceof ApiError) {
    if (action === "connect" && error.status === 502) return TELEGRAM_NOT_CONFIGURED_MESSAGE
    if (action === "test" && error.status === 409) return TELEGRAM_NOT_CONNECTED_MESSAGE
    if (action === "test" && error.status === 502) return TELEGRAM_SEND_FAILED_MESSAGE
  }
  return describeApiError(error)
}
