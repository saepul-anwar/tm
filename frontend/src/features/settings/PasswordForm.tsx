"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useState } from "react"
import { useForm } from "react-hook-form"

import { FormField } from "@/components/forms/FormField"
import { applyApiError } from "@/components/forms/form-errors"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/lib/auth/AuthProvider"
import { changePasswordSchema, type ChangePasswordValues } from "@/lib/auth/schemas"

import { updateProfile } from "./settingsApi"

const FIELDS = ["currentPassword", "newPassword"] as const
/** Long enough to read the success message before the redirect. */
export const LOGOUT_DELAY_MS = 1_500
export const PASSWORD_CHANGED_LOGIN_URL = "/login?notice=password-changed"

export function PasswordForm() {
  const { logout } = useAuth()
  const [formError, setFormError] = useState<string | null>(null)
  const [changed, setChanged] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  })

  async function onSubmit(values: ChangePasswordValues) {
    setFormError(null)
    try {
      await updateProfile({ currentPassword: values.currentPassword, newPassword: values.newPassword })
    } catch (error) {
      // A wrong current password is a 400 field error, not a 401: the session stays intact.
      setFormError(applyApiError(error, setError, FIELDS))
      return
    }
    // The backend revoked every refresh token, so this session cannot be renewed: sign in again.
    reset()
    setChanged(true)
    await new Promise((resolve) => setTimeout(resolve, LOGOUT_DELAY_MS))
    await logout(PASSWORD_CHANGED_LOGIN_URL)
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex max-w-md flex-col gap-4">
      <FormField
        id="password-currentPassword"
        label="Kata sandi saat ini"
        type="password"
        autoComplete="current-password"
        error={errors.currentPassword}
        {...register("currentPassword")}
      />
      <FormField
        id="password-newPassword"
        label="Kata sandi baru"
        type="password"
        autoComplete="new-password"
        error={errors.newPassword}
        {...register("newPassword")}
      />
      <FormField
        id="password-confirmPassword"
        label="Konfirmasi kata sandi baru"
        type="password"
        autoComplete="new-password"
        error={errors.confirmPassword}
        {...register("confirmPassword")}
      />
      <p className="text-xs text-muted-foreground">
        Kata sandi 8 sampai 72 byte, memuat minimal 1 huruf dan 1 angka. Setelah diganti, Anda harus masuk kembali di semua perangkat.
      </p>
      {formError && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {formError}
        </p>
      )}
      {changed && (
        <p role="status" className="text-sm text-muted-foreground">
          Kata sandi diubah. Anda akan diarahkan ke halaman masuk...
        </p>
      )}
      <div>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Memproses..." : "Ganti kata sandi"}
        </Button>
      </div>
    </form>
  )
}
