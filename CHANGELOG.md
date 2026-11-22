# Changelog

Per fase (bukan per rilis; belum ada rilis bertag). Format: ringkasan perubahan yang terlihat pengguna atau operator. Hash commit dari riwayat git.

## Phase 8: Hardening dan konfigurasi lokal (Task 19-20)
- Keamanan: batas ukuran body request 128 KiB (413 `PAYLOAD_TOO_LARGE`); token pengguna terhapus kini 401, bukan 500; tes lintas-user, JWT, header, error, injeksi, actuator (`085f94a`). `shadcn` CLI dipindah ke `devDependencies` (`37a1354`).
- Infrastruktur: MySQL 8.0 -> **8.4 LTS** (upgrade in-place diverifikasi); semua base image dipin ke digest; port host dapat diatur (`FRONTEND_PORT`, `BACKEND_PORT`, tetap 127.0.0.1); hardening Compose (no-new-privileges, read-only, cap_drop, batas memori/proses, rotasi log).
- Operasi: `scripts/backup-db.sh` dan `scripts/restore-db.sh`; `backups/` di-.gitignore.
- Dokumen: README, DEPLOYMENT_GUIDE (operasi lokal + daftar "Sebelum online"), pembaruan docs arsitektur/DB/API/roadmap, `ACCEPTANCE_REPORT.md`.
- Keputusan: hosting LOKAL saja; Spring Boot tetap 3.5.x; tidak ada profil `prod`/reverse proxy/webhook.

## Phase 7: Kalender
- `GET /api/calendar/tasks` (maks 62 hari, 1000 task) dan halaman `/calendar` (bulan, minggu, hari) (`cd86af7`, `57bb34b`).

## Phase 6: Reminder
- Klaim reminder anti-duplikat, pengiriman di luar transaksi, retry dengan backoff (`5afdc1d`); scheduler polling, query kandidat, pembersihan log harian (`59ba3c8`).

## Phase 5: Telegram, profil, pengaturan
- API profil dan pengaturan reminder (`c7098be`); linking Telegram (deep link), webhook dan polling, disconnect, pesan tes (`3f5c072`); halaman settings (`f5ef40c`); penguatan kredensial profil dan webhook (`70a15b1`).

## Phase 4: Dashboard
- Kategori deadline dan API dashboard (`7d54005`); halaman dashboard (`ab60db5`).

## Phase 3: Task
- CRUD dengan pengecekan kepemilikan (`f981aca`); filter, sort, paginasi (`4c1adfe`); halaman task (`8ef9924`).

## Phase 2: Auth
- Envelope respons dan dasar keamanan (`88bff5e`); JWT dengan refresh token berotasi (`f47f720`); penguatan rate limit, cek Origin, klaim JWT (`638a200`); alur autentikasi frontend (`68befdf`).

## Phase 1: Fondasi
- Desain dan rencana (`2b7f73c`); skeleton backend dengan Flyway V1 (`f72e6e1`); skeleton frontend (`dd1bafe`); Docker Compose dan dokumen proyek (`641dce8`).
