"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { useForm } from "react-hook-form"

import { Button } from "@/components/ui/button"
import { useAuth } from "@/lib/auth/AuthProvider"
import { registerSchema, type RegisterValues } from "@/lib/auth/schemas"

import { FormField } from "./FormField"
import { applyApiError } from "./form-errors"

const FIELDS = ["fullName", "email", "password", "confirmPassword"] as const

export function RegisterForm() {
  const { register: registerAccount } = useAuth()
  const router = useRouter()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", email: "", password: "", confirmPassword: "" },
  })

  async function onSubmit(values: RegisterValues) {
    setFormError(null)
    try {
      await registerAccount(values)
      router.replace("/dashboard")
    } catch (error) {
      setFormError(applyApiError(error, setError, FIELDS))
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
      <FormField id="fullName" label="Nama lengkap" autoComplete="name" error={errors.fullName} {...register("fullName")} />
      <FormField id="email" label="Email" type="email" autoComplete="email" error={errors.email} {...register("email")} />
      <FormField
        id="password"
        label="Kata sandi"
        type="password"
        autoComplete="new-password"
        error={errors.password}
        {...register("password")}
      />
      <FormField
        id="confirmPassword"
        label="Konfirmasi kata sandi"
        type="password"
        autoComplete="new-password"
        error={errors.confirmPassword}
        {...register("confirmPassword")}
      />
      <p className="text-xs text-muted-foreground">
        Kata sandi 8 sampai 72 byte, memuat minimal 1 huruf dan 1 angka.
      </p>
      {formError && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {formError}
        </p>
      )}
      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Memproses..." : "Daftar"}
      </Button>
      <p className="text-center text-sm text-muted-foreground">
        Sudah punya akun?{" "}
        <Link href="/login" className="font-medium text-foreground underline-offset-4 hover:underline">
          Masuk
        </Link>
      </p>
    </form>
  )
}
