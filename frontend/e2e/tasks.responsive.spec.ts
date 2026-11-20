import { expect, test, type Page } from "@playwright/test"

// Needs the full stack (frontend + backend + DB), e.g. via docker compose; uses a throwaway
// synthetic account. No browsers are downloaded by this repo's install.
const PASSWORD = process.env.E2E_PASSWORD ?? "Contoh12345"

const SIZES = [
  { name: "375px", width: 375, height: 812 },
  { name: "1280px", width: 1280, height: 800 },
]

async function signUpWithTasks(page: Page) {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  const api = page.request

  const registered = await api.post("/api/auth/register", {
    data: { fullName: "Pengguna Uji", email, password: PASSWORD, confirmPassword: PASSWORD },
  })
  expect(registered.ok()).toBeTruthy()

  const login = await api.post("/api/auth/login", { data: { email, password: PASSWORD } })
  expect(login.ok()).toBeTruthy()
  const { accessToken } = (await login.json()).data as { accessToken: string }

  const priorities = ["LOW", "MEDIUM", "HIGH", "URGENT"]
  for (const [index, priority] of priorities.entries()) {
    const created = await api.post("/api/tasks", {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        title: `Tugas contoh dengan judul yang cukup panjang untuk menguji pembungkusan teks nomor ${index + 1}`,
        description: "Deskripsi sintetis.",
        subject: "Mata Kuliah Contoh",
        deadline: `2026-12-0${index + 1}T16:59:00+07:00`,
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

test.describe("task pages responsive layout", () => {
  for (const size of SIZES) {
    test(`no_horizontal_scroll_at_${size.name}`, async ({ page }) => {
      await page.setViewportSize({ width: size.width, height: size.height })
      await signUpWithTasks(page)

      for (const path of ["/tasks", "/tasks/new", "/completed"]) {
        await page.goto(path)
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible()
        expect(await hasHorizontalScroll(page), `${path} overflows horizontally at ${size.name}`).toBe(false)
      }
    })
  }
})
