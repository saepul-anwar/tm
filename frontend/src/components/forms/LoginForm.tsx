"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { useForm } from "react-hook-form"

import { Button } from "@/components/ui/button"
import { useAuth } from "@/lib/auth/AuthProvider"
import { loginSchema, type LoginValues } from "@/lib/auth/schemas"

import { FormField } from "./FormField"
import { applyApiError } from "./form-errors"

const INVALID_CREDENTIALS = "Email atau kata sandi salah."

export function LoginForm() {
  const { login } = useAuth()
  const router = useRouter()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  })

  async function onSubmit(values: LoginValues) {
    setFormError(null)
    try {
      await login(values.email, values.password)
      router.replace("/dashboard")
    } catch (error) {
      setFormError(applyApiError(error, setError, ["email", "password"], INVALID_CREDENTIALS))
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
      <FormField id="email" label="Email" type="email" autoComplete="email" error={errors.email} {...register("email")} />
      <FormField
        id="password"
        label="Kata sandi"
        type="password"
        autoComplete="current-password"
        error={errors.password}
        {...register("password")}
      />
      {formError && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {formError}
        </p>
      )}
      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Memproses..." : "Masuk"}
      </Button>
      <p className="text-center text-sm text-muted-foreground">
        Belum punya akun?{" "}
        <Link href="/register" className="font-medium text-foreground underline-offset-4 hover:underline">
          Daftar
        </Link>
      </p>
    </form>
  )
}
