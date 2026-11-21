import type { Metadata } from "next"
import { Geist, Geist_Mono } from "next/font/google"
import { Suspense } from "react"

import { AppShell } from "@/components/layout/AppShell"
import { AuthProvider } from "@/lib/auth/AuthProvider"

import "./globals.css"

const geistSans = Geist({ variable: "--font-sans", subsets: ["latin"] })
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] })

export const metadata: Metadata = {
  title: "StudentTask",
  description: "Pengingat tugas kuliah dengan notifikasi Telegram",
}

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="id"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full font-sans">
        {/* The shell reads the pathname; dynamic segments (e.g. /tasks/[id]) need a Suspense boundary to prerender. */}
        <Suspense>
          <AuthProvider>
            <AppShell>{children}</AppShell>
          </AuthProvider>
        </Suspense>
      </body>
    </html>
  )
}
