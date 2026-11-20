import type { Profile } from "./types"

// Synthetic data only. Shared by unit tests; never imported by app code.
export function makeProfile(overrides: Partial<Profile> = {}): Profile {
  return {
    fullName: "Budi Contoh",
    email: "budi@example.test",
    timezone: "Asia/Jakarta",
    telegram: { connected: false, connectedAt: null },
    ...overrides,
  }
}
