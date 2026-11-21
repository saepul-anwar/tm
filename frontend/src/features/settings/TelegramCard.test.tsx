import { act, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { errorEnvelope, jsonResponse, okEnvelope } from "@/features/tasks/test-fixtures"

import { TelegramCard } from "./TelegramCard"
import { makeProfile } from "./test-fixtures"

const NOW = new Date("2026-11-20T10:00:00Z")
const DEEP_LINK = "https://t.me/studenttask_test_bot?start=SYNTHETIC_TOKEN_FOR_TESTS"
const expiresAt = () => new Date(NOW.getTime() + 10 * 60_000).toISOString()
const CONNECTED_AT = "2026-11-20T10:05:00Z"

const fetchMock = vi.fn()
const callsTo = (path: string, method = "GET") =>
  fetchMock.mock.calls.filter(([url, init]) => url === path && ((init as RequestInit)?.method ?? "GET") === method)

const connectResponse = (deepLink = DEEP_LINK) => okEnvelope({ deepLink, expiresAt: expiresAt() })
const notConnected = () => okEnvelope(makeProfile())
const connected = () => okEnvelope(makeProfile({ telegram: { connected: true, connectedAt: CONNECTED_AT } }))

function setup(initial = { connected: false, connectedAt: null as string | null }) {
  const actor = userEvent.setup()
  const view = render(<TelegramCard initial={initial} timeZone="Asia/Jakarta" />)
  return { actor, ...view }
}

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
}

beforeEach(() => {
  // Only the interval timers and Date are faked: Testing Library's waitFor/findBy rely on real setTimeout.
  vi.useFakeTimers({ now: NOW, toFake: ["setInterval", "clearInterval", "Date"] })
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe("TelegramCard", () => {
  it("TelegramCard_shows_deeplink_and_countdown_after_connect", async () => {
    fetchMock.mockResolvedValueOnce(connectResponse())
    const { actor } = setup()

    await actor.click(screen.getByRole("button", { name: "Hubungkan Telegram" }))

    const link = await screen.findByRole("link", { name: /Buka Telegram/ })
    expect(link).toHaveAttribute("href", DEEP_LINK)
    expect(link).toHaveAttribute("target", "_blank")
    expect(link).toHaveAttribute("rel", "noopener noreferrer")
    expect(screen.getByText(/10:00/)).toBeInTheDocument()
    expect(callsTo("/api/telegram/connect", "POST")).toHaveLength(1)

    await advance(5_000)
    expect(screen.getByText(/09:55/)).toBeInTheDocument()
  })

  it("TelegramCard_polls_profile_every_3s_and_shows_connected", async () => {
    fetchMock.mockResolvedValueOnce(connectResponse())
    const { actor } = setup()
    await actor.click(screen.getByRole("button", { name: "Hubungkan Telegram" }))
    await screen.findByRole("link", { name: /Buka Telegram/ })

    fetchMock.mockResolvedValueOnce(notConnected())
    await advance(2_900)
    expect(callsTo("/api/profile")).toHaveLength(0)
    await advance(100)
    expect(callsTo("/api/profile")).toHaveLength(1)
    expect(screen.queryByText(/Terhubung sejak/)).not.toBeInTheDocument()

    fetchMock.mockResolvedValueOnce(connected())
    await advance(3_000)
    expect(callsTo("/api/profile")).toHaveLength(2)
    expect(screen.getByText("Terhubung sejak 20 Nov 2026, 17.05")).toBeInTheDocument()
    expect(screen.queryByRole("link", { name: /Buka Telegram/ })).not.toBeInTheDocument()

    await advance(9_000)
    expect(callsTo("/api/profile")).toHaveLength(2)
  })

  it("TelegramCard_offers_reconnect_after_expiry", async () => {
    fetchMock.mockResolvedValueOnce(connectResponse())
    fetchMock.mockImplementation(() => Promise.resolve(notConnected()))
    const { actor } = setup()
    await actor.click(screen.getByRole("button", { name: "Hubungkan Telegram" }))
    await screen.findByRole("link", { name: /Buka Telegram/ })

    await advance(10 * 60_000 + 1_000)

    expect(screen.getByText("Tautan kedaluwarsa")).toBeInTheDocument()
    expect(screen.queryByRole("link", { name: /Buka Telegram/ })).not.toBeInTheDocument()
    const pollsAtExpiry = callsTo("/api/profile").length
    await advance(9_000)
    expect(callsTo("/api/profile")).toHaveLength(pollsAtExpiry)

    fetchMock.mockResolvedValueOnce(connectResponse())
    await actor.click(screen.getByRole("button", { name: "Buat ulang" }))
    expect(await screen.findByRole("link", { name: /Buka Telegram/ })).toBeInTheDocument()
    expect(callsTo("/api/telegram/connect", "POST")).toHaveLength(2)
  })

  it("polling_stops_on_unmount", async () => {
    fetchMock.mockResolvedValueOnce(connectResponse())
    fetchMock.mockImplementation(() => Promise.resolve(notConnected()))
    const { actor, unmount } = setup()
    await actor.click(screen.getByRole("button", { name: "Hubungkan Telegram" }))
    await screen.findByRole("link", { name: /Buka Telegram/ })
    await advance(3_000)
    const polls = callsTo("/api/profile").length
    expect(polls).toBe(1)

    unmount()
    await advance(15_000)

    expect(callsTo("/api/profile")).toHaveLength(polls)
  })

  it("deeplink_not_starting_with_tme_is_not_rendered_as_a_link", async () => {
    for (const bad of ["http://evil.example/x", "https://t.me.evil.example/x", "javascript:alert(1)"]) {
      fetchMock.mockReset()
      fetchMock.mockResolvedValueOnce(connectResponse(bad))
      const { actor, container, unmount } = setup()

      await actor.click(screen.getByRole("button", { name: "Hubungkan Telegram" }))

      expect(await screen.findByRole("alert")).toHaveTextContent("Tautan Telegram tidak valid")
      expect(screen.queryByRole("link")).not.toBeInTheDocument()
      expect(container.querySelector("a")).toBeNull()
      expect(screen.getByRole("button", { name: "Hubungkan Telegram" })).toBeInTheDocument()
      unmount()
    }
  })

  it("connect_502_says_bot_is_not_configured", async () => {
    fetchMock.mockResolvedValueOnce(errorEnvelope(502, "TELEGRAM_UNAVAILABLE"))
    const { actor } = setup()
    await actor.click(screen.getByRole("button", { name: "Hubungkan Telegram" }))
    expect(await screen.findByRole("alert")).toHaveTextContent("Bot Telegram belum dikonfigurasi di server.")
  })

  it("connect_429_shows_rate_limit_message", async () => {
    fetchMock.mockResolvedValueOnce(errorEnvelope(429, "RATE_LIMITED"))
    const { actor } = setup()
    await actor.click(screen.getByRole("button", { name: "Hubungkan Telegram" }))
    expect(await screen.findByRole("alert")).toHaveTextContent("Terlalu banyak percobaan")
  })

  it("disconnect_asks_confirmation", async () => {
    const { actor } = setup({ connected: true, connectedAt: CONNECTED_AT })
    expect(screen.getByText("Terhubung sejak 20 Nov 2026, 17.05")).toBeInTheDocument()

    await actor.click(screen.getByRole("button", { name: "Putuskan" }))
    const dialog = await screen.findByRole("dialog", { name: "Putuskan Telegram?" })
    expect(fetchMock).not.toHaveBeenCalled()

    await actor.click(within(dialog).getByRole("button", { name: "Batal" }))
    expect(fetchMock).not.toHaveBeenCalled()

    await actor.click(screen.getByRole("button", { name: "Putuskan" }))
    fetchMock.mockResolvedValueOnce(jsonResponse(null, 204))
    await actor.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Putuskan" }))

    expect(callsTo("/api/telegram/disconnect", "DELETE")).toHaveLength(1)
    expect(await screen.findByRole("button", { name: "Hubungkan Telegram" })).toBeInTheDocument()
    expect(screen.queryByText(/Terhubung sejak/)).not.toBeInTheDocument()
  })

  it("test_button_shows_status_on_success_and_error", async () => {
    const { actor } = setup({ connected: true, connectedAt: CONNECTED_AT })
    const send = () => actor.click(screen.getByRole("button", { name: "Kirim pesan uji" }))

    fetchMock.mockResolvedValueOnce(okEnvelope({ sent: true }))
    await send()
    expect(await screen.findByRole("status")).toHaveTextContent("Pesan uji terkirim")
    expect(callsTo("/api/telegram/test", "POST")).toHaveLength(1)

    fetchMock.mockResolvedValueOnce(errorEnvelope(409, "CONFLICT"))
    await send()
    expect(await screen.findByText("Telegram belum terhubung")).toBeInTheDocument()
    expect(screen.getByRole("status")).toHaveTextContent("Telegram belum terhubung")

    fetchMock.mockResolvedValueOnce(errorEnvelope(502, "TELEGRAM_UNAVAILABLE"))
    await send()
    expect(await screen.findByText("Gagal mengirim ke Telegram")).toBeInTheDocument()

    fetchMock.mockResolvedValueOnce(errorEnvelope(429, "RATE_LIMITED"))
    await send()
    expect(await screen.findByText(/Terlalu banyak percobaan/)).toBeInTheDocument()
  })
})
