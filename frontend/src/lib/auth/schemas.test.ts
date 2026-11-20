import { describe, expect, it } from "vitest"

import { loginSchema, registerSchema } from "./schemas"

const validRegister = {
  fullName: "Budi Santoso",
  email: "budi@example.com",
  password: "rahasia123",
  confirmPassword: "rahasia123",
}

function messagesFor(result: { success: boolean; error?: { issues: { path: PropertyKey[]; message: string }[] } }, field: string) {
  return (result.error?.issues ?? [])
    .filter((issue) => issue.path[0] === field)
    .map((issue) => issue.message)
}

describe("loginSchema", () => {
  it("loginSchema_rejects_invalid_email_and_empty_password", () => {
    const result = loginSchema.safeParse({ email: "bukan-email", password: "" })

    expect(result.success).toBe(false)
    expect(messagesFor(result, "email")).not.toHaveLength(0)
    expect(messagesFor(result, "password")).not.toHaveLength(0)
  })

  it("accepts_valid_credentials", () => {
    expect(
      loginSchema.safeParse({ email: "budi@example.com", password: "x" }).success,
    ).toBe(true)
  })
})

describe("registerSchema", () => {
  it("registerSchema_rejects_mismatch_short_and_over_72_bytes_password", () => {
    const mismatch = registerSchema.safeParse({ ...validRegister, confirmPassword: "lain12345" })
    expect(mismatch.success).toBe(false)
    expect(messagesFor(mismatch, "confirmPassword")).not.toHaveLength(0)

    const short = registerSchema.safeParse({ ...validRegister, password: "abc123", confirmPassword: "abc123" })
    expect(short.success).toBe(false)
    expect(messagesFor(short, "password")).not.toHaveLength(0)

    // 30 emoji + digit + letter: only ~32 characters but 4 bytes each in UTF-8 (> 72 bytes).
    const emoji = `${"😀".repeat(30)}a1`
    expect(emoji.length).toBeLessThan(72)
    const tooManyBytes = registerSchema.safeParse({ ...validRegister, password: emoji, confirmPassword: emoji })
    expect(tooManyBytes.success).toBe(false)
    expect(messagesFor(tooManyBytes, "password")).not.toHaveLength(0)
  })

  it("counts_bytes_not_characters_at_the_72_byte_boundary", () => {
    const at72 = `${"a".repeat(71)}1`
    const at73 = `${"a".repeat(72)}1`
    expect(registerSchema.safeParse({ ...validRegister, password: at72, confirmPassword: at72 }).success).toBe(true)
    expect(registerSchema.safeParse({ ...validRegister, password: at73, confirmPassword: at73 }).success).toBe(false)

    // 5 characters but 9 bytes in UTF-8: long enough because the minimum is also counted in bytes.
    const multibyte = "éééé1"
    expect(multibyte.length).toBeLessThan(8)
    expect(registerSchema.safeParse({ ...validRegister, password: multibyte, confirmPassword: multibyte }).success).toBe(true)
  })

  it("requires_a_letter_and_a_digit", () => {
    for (const password of ["12345678", "abcdefgh"]) {
      const result = registerSchema.safeParse({ ...validRegister, password, confirmPassword: password })
      expect(result.success).toBe(false)
      expect(messagesFor(result, "password")).not.toHaveLength(0)
    }
  })

  it("rejects_blank_name_and_invalid_email", () => {
    const result = registerSchema.safeParse({ ...validRegister, fullName: "   ", email: "salah" })
    expect(result.success).toBe(false)
    expect(messagesFor(result, "fullName")).not.toHaveLength(0)
    expect(messagesFor(result, "email")).not.toHaveLength(0)
  })

  it("accepts_valid_input", () => {
    expect(registerSchema.safeParse(validRegister).success).toBe(true)
  })
})
