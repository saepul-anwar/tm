# StudentTask

Web app manajemen tugas kuliah dengan reminder Telegram otomatis. Mahasiswa mendaftar sendiri, mencatat tugas (deadline, prioritas, status), melihat dashboard dan kalender, dan menerima reminder di Telegram (H-24 jam, H-6 jam, H-1 jam, saat deadline) tanpa duplikat.

**Status: MVP selesai untuk pemakaian LOKAL.** Belum siap online (lihat "Sebelum online" di [DEPLOYMENT_GUIDE](docs/DEPLOYMENT_GUIDE.md)). Bukti kriteria penerimaan: [ACCEPTANCE_REPORT](docs/ACCEPTANCE_REPORT.md). Riwayat: [CHANGELOG](CHANGELOG.md).

## Fitur
- Register, login, sesi tetap setelah reload (access token 15 menit di memori, refresh token 7 hari httpOnly dan dirotasi), logout.
- Tugas: buat, ubah, hapus, tandai selesai; judul, deskripsi, mata kuliah, deadline, prioritas (LOW..URGENT), status (TODO, IN_PROGRESS, COMPLETED); filter, cari, urut, paginasi; halaman Completed.
- Dashboard: jumlah per status, terlambat, mendatang, persentase selesai, tugas terdekat dan prioritas tertinggi.
- Kalender: tampilan bulan, minggu (daftar per hari, bukan grid per jam), dan hari; detail tugas lewat drawer.
- Telegram: hubungkan akun lewat deep link, pesan tes, putuskan; reminder otomatis per jenis yang bisa dimatikan.
- Profil dan pengaturan: nama, email, zona waktu IANA, ganti password.
- Isolasi data antar pengguna (404 untuk data milik orang lain).

## Versi yang dipakai
Java 21, Spring Boot 3.5.16, Maven (wrapper), MySQL **8.4 LTS** (dipin ke digest di Compose dan tes), Flyway (bawaan Boot; memberi peringatan "MySQL 8.4 lebih baru dari versi yang diuji", migrasi tetap berjalan), Node 22, Next.js 16.4, React 19, Tailwind 4, Docker Compose. Base image Docker dipin ke digest (lihat Dockerfile dan `docker-compose.yml`).

## Mulai cepat (lokal)
```bash
cp .env.example .env          # isi nilai lokal (jangan di-commit); daftar variabel di bawah
docker compose up -d --build
docker compose ps             # mysql, backend, frontend harus (healthy)
```
Buka http://localhost:3000 (backend: http://localhost:8080/actuator/health). Port host bisa diubah dengan `FRONTEND_PORT` dan `BACKEND_PORT` (tetap terikat ke 127.0.0.1); sesuaikan `CORS_ALLOWED_ORIGINS` dengan alamat yang dibuka di browser.

Cookie refresh memakai flag `Secure` (default `AUTH_REFRESH_COOKIE_SECURE=true`). Chrome tetap menerimanya di http://localhost dan http://127.0.0.1; **Safari tidak**, jadi pengguna Safari mengisi `AUTH_REFRESH_COOKIE_SECURE=false` (hanya lokal).

## Variabel environment (nama saja; contoh lengkap di `.env.example`)
Wajib: `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `BACKEND_URL`.
Opsional: `FRONTEND_PORT`, `BACKEND_PORT`, `AUTH_REFRESH_COOKIE_SECURE`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_MODE`, `TELEGRAM_PUBLIC_BASE_URL`, `REMINDER_SCHEDULER_ENABLED`, `REMINDER_POLL_INTERVAL`, `REMINDER_CLEANUP_CRON`.
- `TELEGRAM_WEBHOOK_SECRET`, bila diisi, wajib cocok `[A-Za-z0-9_-]{32,256}` (mis. `openssl rand -hex 32`); kalau tidak, backend gagal start tanpa menampilkan nilainya.
- `REMINDER_SCHEDULER_ENABLED=false` mematikan polling reminder dan pembersihan harian. Reminder yang waktunya lebih tua dari 10 menit tidak pernah dikirim terlambat.
- Rincian per variabel: [DEPLOYMENT_GUIDE](docs/DEPLOYMENT_GUIDE.md).

## Operasi lokal
```bash
docker compose up -d --build     # start / terapkan perubahan
docker compose ps                # status dan health
docker compose logs -f backend   # log (rotasi 10 MB x 3 per service)
docker compose stop              # stop, data tetap
docker compose down              # hapus container, data tetap di volume
scripts/backup-db.sh             # backup -> backups/studenttask-<waktu>.sql
scripts/restore-db.sh backups/<file>.sql --yes   # pulihkan (menimpa DB; backup pengaman dibuat dulu)
```
Reset total (menghapus SEMUA data, destruktif): `docker compose down -v`. Jangan dijalankan sebelum membuat backup. Detail: [DEPLOYMENT_GUIDE](docs/DEPLOYMENT_GUIDE.md).

## Test
```bash
export DOCKER_HOST=unix:///Users/admin/.colima/default/docker.sock      # hanya Colima (macOS)
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock       # hanya Colima (macOS)
cd backend && ./mvnw test        # butuh Docker (Testcontainers, MySQL 8.4)
cd frontend && npm test && npm run lint && npx tsc --noEmit
BACKEND_URL=http://localhost:8080 npm run build   # di folder frontend
```
Tes responsif Playwright ada di `frontend/e2e` (`npx playwright test`).

## Batasan yang diketahui
- **Tidak ada lupa-password** (butuh email; ditunda). Akun dengan password yang terlupa tidak bisa dipulihkan.
- Telegram memakai **mode polling** (`getUpdates`); mode webhook ada tetapi belum diuji end-to-end dengan Telegram sungguhan.
- **Satu instance saja**: rate limit di memori dan polling Telegram tidak dibagi antar instance; scheduler aman dari duplikat di level database, tetapi dua instance polling Telegram saling bentrok.
- **Hanya lokal**: tanpa HTTPS, reverse proxy, atau pembatasan IP. Daftar pekerjaan sebelum online ada di [DEPLOYMENT_GUIDE](docs/DEPLOYMENT_GUIDE.md).
- Kalender memuat maksimal 1000 tugas per respons (rentang bulan/minggu/hari); tampilan minggu berupa daftar per hari.
- Hanya antarmuka Bahasa Indonesia; hanya notifikasi Telegram.

## Dokumen
[docs/](docs/): `PROJECT_PLAN`, `SYSTEM_ARCHITECTURE`, `DATABASE_DESIGN`, `ERD`, `API_SPECIFICATION`, `DEVELOPMENT_ROADMAP`, `DEPLOYMENT_GUIDE`, `ACCEPTANCE_REPORT`, `security-review`; desain lengkap dan rencana kerja di `docs/specs/` dan `docs/plans/`.
