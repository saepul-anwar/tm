import type { FieldPath, FieldValues, UseFormSetError } from "react-hook-form"

import { ApiError } from "@/lib/api"

export const RATE_LIMIT_MESSAGE = "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi."
const GENERIC_MESSAGE = "Terjadi kesalahan. Coba lagi."

/**
 * Maps a failed auth request to field errors (backend `details`) and/or a
 * form-level message. Returns the form-level message, or null if every problem
 * was attached to a field.
 */
export function applyApiError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly string[],
  fallbackFor401?: string,
): string | null {
  if (!(error instanceof ApiError)) return GENERIC_MESSAGE
  if (error.status === 429) return RATE_LIMIT_MESSAGE
  if (error.status === 401 && fallbackFor401) return fallbackFor401

  const mapped = error.details.filter((detail) => fields.includes(detail.field))
  mapped.forEach((detail) =>
    setError(detail.field as FieldPath<T>, { type: "server", message: detail.message }),
  )
  if (mapped.length > 0) return null
  return error.code === "NETWORK_ERROR" ? error.message : GENERIC_MESSAGE
}
