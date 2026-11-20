"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useMemo, useState } from "react"
import { useForm, useWatch } from "react-hook-form"
import { z } from "zod"

import { FormField } from "@/components/forms/FormField"
import { applyApiError } from "@/components/forms/form-errors"
import { Button } from "@/components/ui/button"
import { ApiError } from "@/lib/api"
import { useAuth } from "@/lib/auth/AuthProvider"
import { emailField } from "@/lib/auth/schemas"

import { updateProfile } from "./settingsApi"
import type { Profile, ProfileUpdate } from "./types"

const FULL_NAME_MAX = 100
const FIELDS = ["fullName", "email", "currentPassword"] as const

const sameEmail = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase()

function buildSchema(currentEmail: string) {
  return z
    .object({
      fullName: z
        .string()
        .trim()
        .min(1, "Nama lengkap wajib diisi.")
        .max(FULL_NAME_MAX, `Nama lengkap maksimal ${FULL_NAME_MAX} karakter.`),
      email: emailField,
      currentPassword: z.string(),
    })
    .superRefine((value, context) => {
      if (!sameEmail(value.email, currentEmail) && value.currentPassword.length === 0) {
        context.addIssue({
          code: "custom",
          path: ["currentPassword"],
          message: "Kata sandi saat ini wajib diisi untuk mengubah email.",
        })
      }
    })
}

type ProfileValues = z.infer<ReturnType<typeof buildSchema>>

type ProfileFormProps = { profile: Profile; onSaved: (profile: Profile) => void }

export function ProfileForm({ profile, onSaved }: ProfileFormProps) {
  const { refreshUser } = useAuth()
  const schema = useMemo(() => buildSchema(profile.email), [profile.email])
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    control,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: profile.fullName, email: profile.email, currentPassword: "" },
  })

  const emailInput = useWatch({ control, name: "email" }) ?? ""
  const emailChanged = !sameEmail(emailInput, profile.email)

  async function onSubmit(values: ProfileValues) {
    setFormError(null)
    setSaved(false)
    const changed = !sameEmail(values.email, profile.email)
    const update: ProfileUpdate = { fullName: values.fullName }
    if (changed) {
      update.email = values.email
      update.currentPassword = values.currentPassword
    }
    try {
      const next = await updateProfile(update)
      reset({ fullName: next.fullName, email: next.email, currentPassword: "" })
      onSaved(next)
      setSaved(true)
      await refreshUser()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setError("email", { type: "server", message: "Email sudah dipakai akun lain." })
        return
      }
      setFormError(applyApiError(error, setError, FIELDS))
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex max-w-md flex-col gap-4">
      <FormField id="profile-fullName" label="Nama lengkap" autoComplete="name" error={errors.fullName} {...register("fullName")} />
      <FormField id="profile-email" label="Email" type="email" autoComplete="email" error={errors.email} {...register("email")} />
      {emailChanged && (
        <FormField
          id="profile-currentPassword"
          label="Kata sandi saat ini (diperlukan untuk mengubah email)"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword}
          {...register("currentPassword")}
        />
      )}
      {formError && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {formError}
        </p>
      )}
      {saved && (
        <p role="status" className="text-sm text-muted-foreground">
          Profil disimpan.
        </p>
      )}
      <div>
        <Button type="submit" disabled={isSubmitting || !isDirty}>
          {isSubmitting ? "Menyimpan..." : "Simpan profil"}
        </Button>
      </div>
    </form>
  )
}
