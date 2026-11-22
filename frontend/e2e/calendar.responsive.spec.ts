import { expect, test, type Page } from "@playwright/test"

// Needs the full stack (frontend + backend + DB), e.g. via docker compose; uses a throwaway
// synthetic account. No browsers are downloaded by this repo's install.
const PASSWORD = process.env.E2E_PASSWORD ?? "Contoh12345"

const SIZES = [
  { name: "375px", width: 375, height: 812 },
  { name: "768px", width: 768, height: 1024 },
  { name: "1440px", width: 1440, height: 900 },
]

const pad = (value: number) => String(value).padStart(2, "0")

async function signUpWithCalendarTasks(page: Page) {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  const api = page.request

  const registered = await api.post("/api/auth/register", {
    data: { fullName: "Pengguna Uji", email, password: PASSWORD, confirmPassword: PASSWORD },
  })
  expect(registered.ok()).toBeTruthy()

  const login = await api.post("/api/auth/login", { data: { email, password: PASSWORD } })
  expect(login.ok()).toBeTruthy()
  const { accessToken } = (await login.json()).data as { accessToken: string }

  // Several long-titled tasks on today's date (Asia/Jakarta default zone) so cells overflow.
  const now = new Date()
  const day = `${now.getUTCFullYear()}-${pad(now.getUTCMonth() + 1)}-${pad(now.getUTCDate())}`
  const priorities = ["LOW", "MEDIUM", "HIGH", "URGENT", "LOW"]
  for (const [index, priority] of priorities.entries()) {
    const created = await api.post("/api/tasks", {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        title: `Tugas contoh dengan judul yang cukup panjang untuk menguji pemotongan teks nomor ${index + 1}`,
        description: "Deskripsi sintetis.",
        subject: "Mata Kuliah Contoh",
        deadline: `${day}T${pad(8 + index)}:00:00+07:00`,
        priority,
      },
    })
    expect(created.ok()).toBeTruthy()
  }

  await page.goto("/login")
  await page.getByLabel("Email").fill(email)
  await page.getByLabel("Kata sandi").fill(PASSWORD)
  await page.getByRole("button", { name: "Masuk" }).click()
  await page.waitForURL("**/dashboard")
}

const hasHorizontalScroll = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)

test.describe("calendar responsive layout", () => {
  for (const size of SIZES) {
    test(`works_at_${size.name}_without_horizontal_scroll`, async ({ page }) => {
      await page.setViewportSize({ width: size.width, height: size.height })
      await signUpWithCalendarTasks(page)

      for (const view of ["month", "week", "day"]) {
        await page.goto(`/calendar?view=${view}`)
        await expect(page.getByRole("heading", { level: 1, name: "Calendar" })).toBeVisible()
        await expect(page.getByRole("list", { name: "Legenda prioritas" })).toBeVisible()
        if (view === "month") {
          await expect(page.getByRole("table", { name: "Kalender bulanan" })).toBeVisible()
        }
        expect(await hasHorizontalScroll(page), `/calendar?view=${view} overflows horizontally at ${size.name}`).toBe(false)
      }

      // Tapping a date lists its tasks without breaking the layout.
      await page.goto("/calendar?view=month")
      await page.getByRole("button", { name: /tugas$/ }).first().click()
      await expect(page.getByRole("region", { name: /^Tugas \d+ \w+ \d{4}$/ })).toBeVisible()
      expect(await hasHorizontalScroll(page)).toBe(false)
    })
  }
})
