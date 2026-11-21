import type { Metadata } from "next"
import { Suspense } from "react"

import { AuthCard } from "@/components/forms/AuthCard"
import { LoginForm } from "@/components/forms/LoginForm"
import { LoginNotice } from "@/components/forms/LoginNotice"

export const metadata: Metadata = { title: "Masuk | StudentTask" }

export default function LoginPage() {
  return (
    <AuthCard title="Masuk" description="Masuk untuk melihat dan mengelola tugas kuliah Anda.">
      {/* useSearchParams needs a Suspense boundary. */}
      <Suspense>
        <LoginNotice />
      </Suspense>
      <LoginForm />
    </AuthCard>
  )
}
