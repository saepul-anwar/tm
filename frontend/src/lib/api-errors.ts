import { ApiError } from "@/lib/api"

export const RATE_LIMIT_MESSAGE = "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi."
export const GENERIC_ERROR_MESSAGE = "Terjadi kesalahan. Coba lagi."
export const SERVER_ERROR_MESSAGE = "Terjadi kesalahan di server. Coba lagi beberapa saat lagi."

const MESSAGES: Record<string, string> = {
  FORBIDDEN: "Permintaan ditolak. Muat ulang halaman lalu coba lagi.",
  RATE_LIMITED: RATE_LIMIT_MESSAGE,
  NOT_FOUND: "Data tidak ditemukan. Mungkin sudah dihapus.",
  CONFLICT: "Data sudah diubah di tempat lain. Muat ulang lalu coba lagi.",
  UNAUTHENTICATED: "Sesi Anda berakhir. Masuk kembali lalu coba lagi.",
  NETWORK_ERROR: "Tidak dapat terhubung ke server. Periksa koneksi Anda.",
}

/** Maps any thrown value to a clear Indonesian message for the UI. */
export function describeApiError(error: unknown): string {
  if (!(error instanceof ApiError)) return GENERIC_ERROR_MESSAGE
  if (error.code === "VALIDATION_ERROR" && error.message) return error.message
  const byCode = MESSAGES[error.code]
  if (byCode) return byCode
  if (error.status === 429) return RATE_LIMIT_MESSAGE
  if (error.status >= 500) return SERVER_ERROR_MESSAGE
  return GENERIC_ERROR_MESSAGE
}
