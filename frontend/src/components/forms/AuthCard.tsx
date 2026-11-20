import type { ReactNode } from "react"

export function AuthCard({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <section className="mx-auto flex w-full max-w-sm flex-col gap-6 py-4 md:py-12">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        <p className="text-sm text-muted-foreground">{description}</p>
      </header>
      {children}
    </section>
  )
}
