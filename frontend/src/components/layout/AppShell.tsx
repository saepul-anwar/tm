"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { useEffect, useRef, useState, type ReactNode } from "react"
import { LogOut, Menu, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { isPublicPath } from "@/lib/auth/constants"
import { useAuth } from "@/lib/auth/AuthProvider"
import { cn } from "@/lib/utils"

import { NAV_ITEMS } from "./nav-items"

export { NAV_ITEMS }

const APP_NAME = "StudentTask"

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname()

  return (
    <ul className="flex flex-col gap-0.5">
      {NAV_ITEMS.map(({ label, href, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(`${href}/`)
        return (
          <li key={href}>
            <Link
              href={href}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
                active && "bg-muted font-medium text-foreground",
              )}
            >
              <Icon className="size-4" aria-hidden="true" />
              {label}
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

function Brand() {
  return (
    <span className="text-sm font-semibold tracking-tight">{APP_NAME}</span>
  )
}

function LogoutButton({ onDone }: { onDone?: () => void }) {
  const { user, logout } = useAuth()

  return (
    <div className="mt-auto flex flex-col gap-1 border-t pt-3">
      {user && (
        <p className="truncate px-2.5 text-xs text-muted-foreground" title={user.email}>
          {user.fullName}
        </p>
      )}
      <Button
        variant="ghost"
        className="justify-start gap-2.5 px-2.5 text-muted-foreground"
        onClick={() => {
          onDone?.()
          void logout()
        }}
      >
        <LogOut aria-hidden="true" />
        Keluar
      </Button>
    </div>
  )
}

function MobileDrawer({ onClose }: { onClose: () => void }) {
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    closeRef.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose()
    }
    document.addEventListener("keydown", onKeyDown)
    return () => document.removeEventListener("keydown", onKeyDown)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Menu navigasi"
        className="absolute inset-y-0 left-0 flex w-64 flex-col gap-4 border-r bg-background p-3 shadow-lg"
      >
        <div className="flex h-9 items-center justify-between px-1.5">
          <Brand />
          <Button
            ref={closeRef}
            variant="ghost"
            size="icon"
            aria-label="Tutup menu"
            onClick={onClose}
          >
            <X aria-hidden="true" />
          </Button>
        </div>
        <nav aria-label="Navigasi seluler">
          <NavLinks onNavigate={onClose} />
        </nav>
        <LogoutButton onDone={onClose} />
      </div>
    </div>
  )
}

export function AppShell({ children }: { children: ReactNode }) {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const pathname = usePathname()

  if (isPublicPath(pathname)) {
    return (
      <main className="min-h-dvh bg-background px-4 py-8 text-foreground md:px-8">
        <p className="mx-auto mb-6 w-full max-w-sm text-sm font-semibold tracking-tight">{APP_NAME}</p>
        {children}
      </main>
    )
  }

  return (
    <div className="flex min-h-dvh bg-background text-foreground">
      <aside className="sticky top-0 hidden h-dvh w-60 shrink-0 flex-col gap-4 border-r p-3 md:flex">
        <div className="flex h-9 items-center px-1.5">
          <Brand />
        </div>
        <nav aria-label="Navigasi utama">
          <NavLinks />
        </nav>
        <LogoutButton />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-40 flex h-12 items-center gap-2 border-b bg-background/90 px-3 backdrop-blur md:hidden">
          <Button
            variant="ghost"
            size="icon"
            aria-label="Buka menu"
            aria-haspopup="dialog"
            aria-expanded={drawerOpen}
            onClick={() => setDrawerOpen(true)}
          >
            <Menu aria-hidden="true" />
          </Button>
          <Brand />
        </header>
        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 md:px-8 md:py-10">
          {children}
        </main>
      </div>

      {drawerOpen && <MobileDrawer onClose={() => setDrawerOpen(false)} />}
    </div>
  )
}
