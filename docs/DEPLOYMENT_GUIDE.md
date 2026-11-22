# Panduan Deployment

**Status: hanya menjalankan lokal (keputusan pemilik, 2026-11-21).** Tidak ada reverse proxy, HTTPS, atau mode webhook; semua port terikat ke `127.0.0.1`. Jangan mengekspos stack ini ke internet sebelum semua butir "Sebelum online" di bawah selesai.

## Lokal dengan Docker Compose
Prasyarat: Docker + Compose (di macOS dipakai Colima).
```bash
cp .env.example .env        # isi nilai LOKAL; .env tidak di-commit
docker compose up -d --build
docker compose ps            # 3 service: mysql, backend, frontend (healthy)
```
Buka http://localhost:3000. Health backend: `docker compose exec frontend wget -qO- http://backend:8080/actuator/health` atau http://localhost:8080/actuator/health.

Cookie refresh punya flag `Secure` (default `AUTH_REFRESH_COOKIE_SECURE=true`). Chrome menerimanya di http://localhost / http://127.0.0.1; Safari tidak, jadi isi `false` di `.env` hanya bila memakai Safari lewat http lokal, lalu `docker compose up -d`. `CORS_ALLOWED_ORIGINS` harus sama persis dengan alamat di address bar (`localhost` dan `127.0.0.1` berbeda).

## Operasi harian
| Tujuan | Perintah |
|---|---|
| Start / terapkan perubahan | `docker compose up -d --build` |
| Status dan health | `docker compose ps` |
| Log | `docker compose logs -f backend` (juga `frontend`, `mysql`); rotasi `json-file` 10 MB x 3 file per service |
| Stop (data tetap) | `docker compose stop` atau `docker compose down` |
| Cek versi DB | `docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "select version()"'` |
| Reset total | `docker compose down -v` (**menghapus semua data**, hanya lokal; backup dulu) |

Hardening Compose yang aktif: restart `unless-stopped`; log rotasi; `no-new-privileges` di semua service; backend dan frontend `read_only` (dengan `tmpfs /tmp`) dan `cap_drop: ALL`; `mem_limit` (mysql 1g, backend 1g, frontend 512m) dan `pids_limit`; image non-root; MySQL tidak dipublikasikan ke host. Port host: `FRONTEND_PORT` (default 3000), `BACKEND_PORT` (default 8080).

## Backup dan restore
Backup logis (`mysqldump --single-transaction --routines`) lewat service `mysql`; password dibaca di dalam container, tidak muncul di command line host atau output.
```bash
scripts/backup-db.sh                                  # -> backups/studenttask-YYYYmmdd-HHMMSS.sql (izin 600; folder di-.gitignore)
scripts/restore-db.sh backups/<file>.sql --yes        # tanpa --yes ditolak
```
`restore-db.sh` memverifikasi file adalah dump lengkap, membuat backup pengaman dulu, menghentikan backend, memulihkan ke database terkonfigurasi (menimpa tabel yang ada), lalu menyalakan backend lagi. File backup berisi data pengguna (hash password, chat id): simpan di tempat aman, jangan commit. Untuk stack lain, set `COMPOSE_PROJECT_NAME`.

Retensi dan penyalinan ke luar mesin belum diotomatisasi (lihat "Sebelum online"). Backup diuji pada stack terpisah dengan volume baru (backup, hapus baris, restore, baris kembali).

## Upgrade MySQL 8.0 -> 8.4 (sudah dilakukan 2026-11-22 pada volume lokal)
Urutan yang dipakai: backup logis dulu, ganti tag image, `docker compose up -d --build`. MySQL menaikkan data dictionary dan server otomatis (tercatat di log `mysql`), Flyway melaporkan skema versi 1 up to date. Rollback bila gagal: kembalikan image ke tag 8.0 dengan volume dari **sebelum** upgrade, atau restore backup logis ke instalasi baru; data dictionary 8.4 tidak bisa dibaca 8.0, jadi jangan menurunkan versi di atas volume yang sudah dinaikkan.

## Variabel environment (nama saja)
Contoh lengkap dengan komentar: `.env.example`.
- **Wajib** (Compose gagal bila kosong): `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `BACKEND_URL`.
- **Port**: `FRONTEND_PORT`, `BACKEND_PORT`.
- **Auth**: `AUTH_REFRESH_COOKIE_SECURE` (default `true`).
- **Telegram** (kosong = fitur nonaktif): `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_MODE` (default `polling`), `TELEGRAM_PUBLIC_BASE_URL`.
- **Reminder**: `REMINDER_SCHEDULER_ENABLED` (default `true`), `REMINDER_POLL_INTERVAL` (ISO-8601, default `PT60S`), `REMINDER_CLEANUP_CRON` (cron Spring 6 field, UTC, default `0 30 3 * * *`).
- `BACKEND_URL` dipakai frontend saat **build** dan runtime (di Compose: alamat internal service backend). Mengubahnya butuh `docker compose build frontend`.
- `DB_URL` dirakit Compose dari `DB_NAME`.
- `TELEGRAM_WEBHOOK_SECRET`, bila diisi, wajib cocok `[A-Za-z0-9_-]{32,256}` (mis. `openssl rand -hex 32`); jika tidak, backend **gagal start** (pesan error tidak menampilkan nilainya). `TELEGRAM_API_BASE` dan `TELEGRAM_RUNNERS_ENABLED` dibaca aplikasi tetapi tidak diteruskan Compose.
- Reminder: scheduler mengecek tiap `REMINDER_POLL_INTERVAL`; reminder yang waktunya lebih tua dari 10 menit tidak dikirim terlambat, jadi menyalakan scheduler tidak mengirim ulang tugas lama. Log hanya muncul bila ada yang dikirim.

## Verifikasi start dari nol
Stack kedua dengan project dan port berbeda (tidak menyentuh volume utama):
```bash
FRONTEND_PORT=3100 BACKEND_PORT=8180 CORS_ALLOWED_ORIGINS=http://127.0.0.1:3100 BACKEND_URL=http://backend:8080 \
  docker compose -p studenttask-verify up -d --build
docker compose -p studenttask-verify down -v      # hanya project verifikasi
```
Catatan: stack kedua dengan token Telegram yang sama ikut melakukan polling dan bentrok dengan stack utama; kosongkan `TELEGRAM_BOT_TOKEN` untuk stack verifikasi yang lama.

## Kegagalan umum
- `docker compose` gagal dengan "isi ... di .env": variabel wajib kosong.
- Backend tidak start: `docker compose logs backend` (rahasia kosong, `TELEGRAM_WEBHOOK_SECRET` berformat salah, DB belum siap).
- Frontend gagal build: `BACKEND_URL` kosong.
- Login berhasil tetapi sesi hilang setelah reload di Safari: set `AUTH_REFRESH_COOKIE_SECURE=false`.
- Login ditolak 403 pada refresh: origin di address bar tidak ada di `CORS_ALLOWED_ORIGINS`.
- Peringatan Flyway "MySQL 8.4 is newer than this version of Flyway": informasi saja, migrasi berjalan (TODO: verify saat Spring Boot/Flyway dinaikkan).

## Sebelum online (WAJIB; belum dikerjakan)
Sumber: [security-review.md](security-review.md) bagian 2 dan 3. Semua butir terbuka.
- [ ] **HTTPS reverse proxy** (mis. Caddy/Nginx) dengan TLS 1.2+, dan `AUTH_REFRESH_COOKIE_SECURE=true`; backend dan frontend tidak dipublikasikan langsung.
- [ ] **A-F2** Rate limit per-IP memakai `getRemoteAddr()`; di belakang proxy semua user berbagi satu alamat. Teruskan IP klien yang tepercaya (forwarded header yang aman) atau batasi di proxy.
- [ ] **A-F4** Pembatasan per akun saat login dan daftar password umum (pemilik akan ditanya lagi saat memutuskan online).
- [ ] **B-F7** Setel `TELEGRAM_MODE` secara eksplisit di produksi; pakai `webhook` dengan `TELEGRAM_PUBLIC_BASE_URL` HTTPS dan `TELEGRAM_WEBHOOK_SECRET` kuat (dua instance polling bentrok).
- [ ] **B-F9** Batas laju dan/atau daftar IP untuk `/api/telegram/webhook` di reverse proxy (saat ini hanya rahasia header).
- [ ] **C-F2** Dukungan Spring Boot 3.5.x (mungkin di luar dukungan open-source): jalankan OSV-Scanner atau `dependency-check` pada dependensi; rencanakan upgrade ke Boot 4 sebagai pekerjaan terpisah.
- [ ] **C-F4** CSP berbasis nonce (hapus `'unsafe-inline'`) dan header HSTS.
- [ ] **Rotasi rahasia**: `JWT_SECRET`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`, token bot Telegram, `TELEGRAM_WEBHOOK_SECRET` diganti dengan nilai baru khusus produksi (jangan pakai nilai lokal), disimpan di secret manager.
- [ ] **Backup dan retensi**: jadwal otomatis, salinan di luar mesin, enkripsi, uji restore berkala, kebijakan retensi.
- [ ] **Monitoring dan alert**: health, error rate, kegagalan reminder (`notification_logs` FAILED), disk dan memori; log terpusat.
- [ ] Putuskan ulang risiko yang diterima (A-F6, A-F8, B-F5, C-F5) dan fitur lupa-password.
- [ ] Review keamanan ulang (termasuk alat otomatis: gitleaks, semgrep, osv-scanner) dan pemindaian image.
