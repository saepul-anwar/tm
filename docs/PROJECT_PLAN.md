# Rencana Proyek

Sumber: [`specs/2026-11-20-studenttask-design.md`](specs/2026-11-20-studenttask-design.md) §1, §2, §14; rencana kerja: [`plans/2026-11-20-studenttask-implementation.md`](plans/2026-11-20-studenttask-implementation.md).

**StudentTask**: web app manajemen tugas kuliah. Mahasiswa membuat akun sendiri, mencatat tugas (deadline, prioritas, status), melihat dashboard dan kalender, dan menerima reminder Telegram otomatis (H-24 jam, H-6 jam, H-1 jam, saat deadline) tanpa duplikat.

## Tujuan
1. Register, login, dan kelola tugas milik sendiri (CRUD, filter, sort, pagination).
2. Dashboard dan kalender dari data backend sungguhan.
3. Reminder Telegram otomatis, bisa diatur per jenis, tanpa duplikat, tidak untuk tugas selesai.
4. Data antar mahasiswa terisolasi penuh.
5. Berjalan dengan `docker compose up`.

## Bukan tujuan MVP
Admin/dosen/multi-role, verifikasi email, **lupa/reset password**, notifikasi selain Telegram, tugas berulang, lampiran, berbagi tugas, aplikasi native, i18n, message queue.

## Stack
Next.js + TypeScript + Tailwind + shadcn/ui; Java 21 + Spring Boot 3.5.16 + Spring Security + JPA + Flyway; MySQL 8.0; Telegram Bot API; Docker Compose.

## Status
Phase 1 (Fondasi) berjalan; lihat [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md).
Aturan kerja: branch `feature/<slug>`, Conventional Commits, tidak ada commit langsung ke `main`; push dan PR hanya setelah konfirmasi pemilik.
