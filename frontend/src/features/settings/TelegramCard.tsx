"use client"

import { ExternalLink } from "lucide-react"
import { useEffect, useRef, useState } from "react"

import { Button, buttonVariants } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog"
import { formatDateTime } from "@/features/tasks/datetime"
import { describeApiError } from "@/lib/api-errors"
import { cn } from "@/lib/utils"

import { connectTelegram, disconnectTelegram, fetchProfile, sendTelegramTest } from "./settingsApi"
import { describeTelegramError } from "./telegramErrors"
import type { TelegramStatus } from "./types"

const POLL_INTERVAL_MS = 3_000
const COUNTDOWN_INTERVAL_MS = 1_000
const TELEGRAM_LINK_PREFIX = "https://t.me/"

type Phase = "idle" | "pending" | "expired" | "connected"
type Message = { tone: "success" | "error"; text: string }

/** The link carries a one-time token: only ever render it when it points at Telegram. */
function isTelegramLink(value: string): boolean {
  if (!value.startsWith(TELEGRAM_LINK_PREFIX)) return false
  try {
    const url = new URL(value)
    return url.protocol === "https:" && url.hostname === "t.me"
  } catch {
    return false
  }
}

function formatCountdown(remainingMs: number): string {
  const totalSeconds = Math.max(0, Math.ceil(remainingMs / 1000))
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, "0")
  const seconds = String(totalSeconds % 60).padStart(2, "0")
  return `${minutes}:${seconds}`
}

type PendingLink = { deepLink: string; expiresAtMs: number }

type TelegramCardProps = { initial: TelegramStatus; timeZone: string }

export function TelegramCard({ initial, timeZone }: TelegramCardProps) {
  const [phase, setPhase] = useState<Phase>(initial.connected ? "connected" : "idle")
  const [connectedAt, setConnectedAt] = useState<string | null>(initial.connectedAt)
  const [link, setLink] = useState<PendingLink | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<Message | null>(null)
  const [confirmingDisconnect, setConfirmingDisconnect] = useState(false)

  const expiresAtMs = link?.expiresAtMs ?? null
  const pending = phase === "pending"

  // Countdown; flips to "expired" once the one-time link has run out.
  useEffect(() => {
    if (!pending || expiresAtMs === null) return
    const tick = () => {
      const current = Date.now()
      setNow(current)
      if (current >= expiresAtMs) {
        setPhase("expired")
        setLink(null)
      }
    }
    const timer = setInterval(tick, COUNTDOWN_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [pending, expiresAtMs])

  // Poll the profile until the bot reports the account as connected. Stops on unmount, expiry or success.
  const pollInFlight = useRef(false)
  useEffect(() => {
    if (!pending) return
    let active = true
    const poll = async () => {
      if (pollInFlight.current) return
      pollInFlight.current = true
      try {
        const profile = await fetchProfile()
        if (!active || !profile.telegram.connected) return
        setConnectedAt(profile.telegram.connectedAt)
        setLink(null)
        setPhase("connected")
        setMessage({ tone: "success", text: "Telegram berhasil terhubung." })
      } catch {
        // Transient failures are ignored; the next tick retries until the link expires.
      } finally {
        pollInFlight.current = false
      }
    }
    const timer = setInterval(() => void poll(), POLL_INTERVAL_MS)
    return () => {
      active = false
      clearInterval(timer)
    }
  }, [pending])

  async function connect() {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const response = await connectTelegram()
      const expires = Date.parse(response.expiresAt)
      if (!isTelegramLink(response.deepLink) || Number.isNaN(expires)) {
        setError("Tautan Telegram tidak valid. Coba buat ulang.")
        return
      }
      setNow(Date.now())
      setLink({ deepLink: response.deepLink, expiresAtMs: expires })
      setPhase("pending")
    } catch (caught) {
      setError(describeTelegramError(caught, "connect"))
    } finally {
      setBusy(false)
    }
  }

  async function sendTest() {
    setBusy(true)
    setMessage(null)
    try {
      await sendTelegramTest()
      setMessage({ tone: "success", text: "Pesan uji terkirim. Periksa Telegram Anda." })
    } catch (caught) {
      setMessage({ tone: "error", text: describeTelegramError(caught, "test") })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-3">
      {error && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      )}

      {phase === "idle" && (
        <>
          <p className="text-sm text-muted-foreground">
            Hubungkan akun Telegram agar pengingat tugas dikirim ke chat Anda.
          </p>
          <div>
            <Button onClick={() => void connect()} disabled={busy}>
              {busy ? "Memproses..." : "Hubungkan Telegram"}
            </Button>
          </div>
        </>
      )}

      {phase === "pending" && link && (
        <>
          <p className="text-sm text-muted-foreground">
            Buka Telegram lewat tombol di bawah, lalu tekan Start pada bot. Halaman ini akan terbarui otomatis.
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <a
              href={link.deepLink}
              target="_blank"
              rel="noopener noreferrer"
              className={cn(buttonVariants())}
            >
              Buka Telegram
              <ExternalLink aria-hidden="true" />
            </a>
            <span className="text-sm text-muted-foreground">
              Berlaku <span className="font-mono tabular-nums">{formatCountdown(link.expiresAtMs - now)}</span>
            </span>
          </div>
        </>
      )}

      {phase === "expired" && (
        <>
          <p className="text-sm font-medium">Tautan kedaluwarsa</p>
          <div>
            <Button onClick={() => void connect()} disabled={busy}>
              {busy ? "Memproses..." : "Buat ulang"}
            </Button>
          </div>
        </>
      )}

      {phase === "connected" && (
        <>
          <p className="text-sm">Terhubung sejak {formatDateTime(connectedAt, timeZone)}</p>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => void sendTest()} disabled={busy}>
              Kirim pesan uji
            </Button>
            <Button variant="destructive" onClick={() => setConfirmingDisconnect(true)} disabled={busy}>
              Putuskan
            </Button>
          </div>
        </>
      )}

      {message && (
        <p
          role="status"
          className={cn("text-sm", message.tone === "error" ? "text-destructive" : "text-muted-foreground")}
        >
          {message.text}
        </p>
      )}

      <DisconnectDialog
        open={confirmingDisconnect}
        onClose={() => setConfirmingDisconnect(false)}
        onDisconnected={() => {
          setConfirmingDisconnect(false)
          setConnectedAt(null)
          setMessage(null)
          setPhase("idle")
        }}
      />
    </div>
  )
}

type DisconnectDialogProps = { open: boolean; onClose: () => void; onDisconnected: () => void }

function DisconnectDialog({ open, onClose, onDisconnected }: DisconnectDialogProps) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function close() {
    if (pending) return
    setError(null)
    onClose()
  }

  async function confirm() {
    setPending(true)
    setError(null)
    try {
      await disconnectTelegram()
      onDisconnected()
    } catch (caught) {
      setError(describeApiError(caught))
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && close()}>
      <DialogContent>
        <DialogTitle>Putuskan Telegram?</DialogTitle>
        <DialogDescription>
          Pengingat tidak akan dikirim ke Telegram sampai Anda menghubungkannya kembali.
        </DialogDescription>
        {error && (
          <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={close} disabled={pending}>
            Batal
          </Button>
          <Button variant="destructive" onClick={() => void confirm()} disabled={pending}>
            {pending ? "Memutuskan..." : "Putuskan"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
