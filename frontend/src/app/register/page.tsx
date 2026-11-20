import type { Metadata } from "next"

import { AuthCard } from "@/components/forms/AuthCard"
import { RegisterForm } from "@/components/forms/RegisterForm"

export const metadata: Metadata = { title: "Daftar | StudentTask" }

export default function RegisterPage() {
  return (
    <AuthCard title="Daftar" description="Buat akun StudentTask untuk mulai mencatat tugas.">
      <RegisterForm />
    </AuthCard>
  )
}
