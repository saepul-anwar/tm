# StudentTask

Web app manajemen tugas kuliah dengan reminder Telegram otomatis. Status: **Phase 1 (fondasi)**; belum ada fitur API selain health check.

Dokumen: [docs/](docs/) (`PROJECT_PLAN`, `SYSTEM_ARCHITECTURE`, `DATABASE_DESIGN`, `ERD`, `API_SPECIFICATION`, `DEVELOPMENT_ROADMAP`, `DEPLOYMENT_GUIDE`); desain lengkap di `docs/specs/`.

## Versi yang dipakai
Java 21, Spring Boot 3.5.16, Maven (wrapper), MySQL **8.0** (dipin di Compose; Testcontainers saat ini memakai tag `mysql:8`, TODO: dipin ke 8.0), Node 22, Next.js 16, React 19, Tailwind 4, Docker Compose.

## Jalankan lokal
```bash
cp .env.example .env          # isi nilai lokal (jangan di-commit)
docker compose up -d --build
```
Frontend http://localhost:3000, backend http://localhost:8080/actuator/health. Detail: [DEPLOYMENT_GUIDE](docs/DEPLOYMENT_GUIDE.md).

## Variabel environment (nama saja)
`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_MODE`, `TELEGRAM_PUBLIC_BASE_URL`, `REMINDER_SCHEDULER_ENABLED`, `REMINDER_POLL_INTERVAL`, `REMINDER_CLEANUP_CRON`, `BACKEND_URL`.
- Reminder: `REMINDER_SCHEDULER_ENABLED` (default `true`) menyalakan polling reminder (tiap `REMINDER_POLL_INTERVAL`, format ISO-8601, default `PT60S`) dan pembersihan harian (`REMINDER_CLEANUP_CRON`, cron Spring 6 field, UTC, default `0 30 3 * * *`). Isi `false` untuk mematikan keduanya. Reminder yang waktunya lebih tua dari 10 menit tidak pernah dikirim terlambat, jadi menyalakan scheduler tidak mengirim ulang tugas lama. Start pertama menunggu satu interval; log hanya muncul bila ada yang dikirim.
- `TELEGRAM_WEBHOOK_SECRET` wajib cocok `[A-Za-z0-9_-]{32,256}` (mis. `openssl rand -hex 32`) dan `TELEGRAM_API_BASE` (opsional) wajib `https://`; kalau tidak, backend gagal start tanpa menampilkan nilainya.

## Test
```bash
cd backend && ./mvnw test        # butuh Docker (Testcontainers, MySQL 8.0)
cd frontend && npm test
```
Catatan Colima (macOS): backend test butuh
```bash
export DOCKER_HOST=unix:///Users/admin/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Batasan MVP
**Tidak ada lupa-password di MVP** (butuh email; ditunda). Akun dengan password yang terlupa tidak bisa dipulihkan.
