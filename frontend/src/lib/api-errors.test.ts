import { describe, expect, it } from "vitest"

import { ApiError } from "./api"
import { describeApiError } from "./api-errors"

const err = (status: number, code: string, message = "Pesan server.") => new ApiError(status, code, message)

describe("describeApiError", () => {
  it("maps_known_codes_to_indonesian_messages", () => {
    expect(describeApiError(err(403, "FORBIDDEN"))).toMatch(/^Permintaan ditolak/)
    expect(describeApiError(err(429, "RATE_LIMITED"))).toBe("Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi.")
    expect(describeApiError(err(404, "NOT_FOUND"))).toMatch(/^Data tidak ditemukan/)
    expect(describeApiError(err(409, "CONFLICT"))).toMatch(/diubah di tempat lain/)
    expect(describeApiError(err(401, "UNAUTHENTICATED"))).toMatch(/Sesi/)
    expect(describeApiError(err(0, "NETWORK_ERROR"))).toMatch(/Tidak dapat terhubung/)
  })

  it("uses_a_generic_server_message_for_5xx_and_a_fallback_otherwise", () => {
    expect(describeApiError(err(500, "INTERNAL_ERROR"))).toMatch(/^Terjadi kesalahan di server/)
    expect(describeApiError(err(502, "TELEGRAM_UNAVAILABLE"))).toMatch(/^Terjadi kesalahan di server/)
    expect(describeApiError(new Error("x"))).toBe("Terjadi kesalahan. Coba lagi.")
    expect(describeApiError(err(400, "UNKNOWN_ERROR"))).toBe("Terjadi kesalahan. Coba lagi.")
  })

  it("keeps_the_server_message_for_validation_errors", () => {
    expect(describeApiError(err(400, "VALIDATION_ERROR", "Parameter size harus antara 1 dan 50."))).toBe(
      "Parameter size harus antara 1 dan 50.",
    )
  })
})
