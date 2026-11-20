import { z } from "zod"

// Mirrors backend rules (StrongPasswordValidator, RegisterRequest, LoginRequest).
const PASSWORD_MIN_BYTES = 8
const PASSWORD_MAX_BYTES = 72 // BCrypt limit: longer input must be rejected, not truncated.
const FULL_NAME_MAX = 100
const EMAIL_MAX = 254

const utf8Encoder = new TextEncoder()
const utf8Bytes = (value: string) => utf8Encoder.encode(value).length

export const emailField = z
  .string()
  .trim()
  .min(1, "Email wajib diisi.")
  .max(EMAIL_MAX, `Email maksimal ${EMAIL_MAX} karakter.`)
  .pipe(z.email("Format email tidak valid."))

export const passwordField = z
  .string()
  .refine((value) => utf8Bytes(value) >= PASSWORD_MIN_BYTES, "Kata sandi minimal 8 byte.")
  .refine(
    (value) => utf8Bytes(value) <= PASSWORD_MAX_BYTES,
    "Kata sandi terlalu panjang (maksimal 72 byte; karakter seperti emoji memakan beberapa byte).",
  )
  .refine((value) => /\p{L}/u.test(value), "Kata sandi harus memuat minimal 1 huruf.")
  .refine((value) => /\p{Nd}/u.test(value), "Kata sandi harus memuat minimal 1 angka.")

export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, "Kata sandi wajib diisi."),
})

export const registerSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(1, "Nama lengkap wajib diisi.")
      .max(FULL_NAME_MAX, `Nama lengkap maksimal ${FULL_NAME_MAX} karakter.`),
    email: emailField,
    password: passwordField,
    confirmPassword: z.string(),
  })
  .refine((value) => value.password === value.confirmPassword, {
    path: ["confirmPassword"],
    message: "Konfirmasi kata sandi tidak sama.",
  })

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Kata sandi saat ini wajib diisi."),
    newPassword: passwordField,
    confirmPassword: z.string(),
  })
  .refine((value) => value.newPassword === value.confirmPassword, {
    path: ["confirmPassword"],
    message: "Konfirmasi kata sandi tidak sama.",
  })

export type LoginValues = z.infer<typeof loginSchema>
export type RegisterValues = z.infer<typeof registerSchema>
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>
