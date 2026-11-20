import type { Metadata } from "next"

import { AuthCard } from "@/components/forms/AuthCard"
import { LoginForm } from "@/components/forms/LoginForm"

export const metadata: Metadata = { title: "Masuk | StudentTask" }

export default function LoginPage() {
  return (
    <AuthCard title="Masuk" description="Masuk untuk melihat dan mengelola tugas kuliah Anda.">
      <LoginForm />
    </AuthCard>
  )
}
