# Panduan Deployment

**Status: hanya menjalankan lokal.** Keputusan hosting (lokal/demo vs online) belum diambil; ditentukan pemilik sebelum Task 20 (profil `prod`, HTTPS, webhook Telegram). Jangan menganggap konfigurasi saat ini siap produksi.

## Lokal dengan Docker Compose
Prasyarat: Docker + Compose (di macOS dipakai Colima).
```bash
cp .env.example .env        # isi nilai LOKAL; .env tidak di-commit
docker compose up -d --build
docker compose ps            # 3 service: mysql, backend, frontend (healthy)
curl -fs localhost:8080/actuator/health
```
Buka http://localhost:3000.

Stop: `docker compose down` (data tetap di volume `mysql_data`). Hapus data: `docker compose down -v` (destruktif, hanya lokal).

## Variabel environment (nama saja)
`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_MODE`, `TELEGRAM_PUBLIC_BASE_URL`, `BACKEND_URL`.
- `BACKEND_URL` dipakai frontend saat **build** dan runtime (di Compose: alamat internal service backend). Mengubahnya butuh `docker compose build frontend`.
- `DB_URL` dirakit Compose dari `DB_NAME`.
- `TELEGRAM_PUBLIC_BASE_URL`: origin HTTPS publik backend; hanya dipakai saat `TELEGRAM_MODE=webhook` (kosong pada mode polling). `TELEGRAM_WEBHOOK_SECRET` wajib cocok `[A-Za-z0-9_-]{32,256}` (mis. `openssl rand -hex 32`); jika tidak, backend **gagal start** (pesan error tidak menampilkan nilainya). Nilai pendek/placeholder di `.env` lokal harus diganti. `TELEGRAM_API_BASE` (opsional) harus `https://`; backend menolak `http://` saat start.

## Jaringan dan keamanan lokal
- `frontend` :3000 dan `backend` :8080 hanya terikat ke `127.0.0.1`; MySQL tidak dipublikasikan.
- Image backend dan frontend berjalan sebagai user non-root.

## Healthcheck
mysql (`mysqladmin ping`), backend (`/actuator/health` berisi `UP`), frontend (HTTP 200 pada `/`).

## Kegagalan umum
- `docker compose` gagal dengan "isi ... di .env": variabel wajib kosong.
- Backend tidak start: periksa `docker compose logs backend` (rahasia kosong atau DB belum siap).
- Frontend gagal build: `BACKEND_URL` kosong.

## TODO (Task 20)
Profil `prod`, reverse proxy HTTPS, mode webhook, backup volume MySQL, prosedur rollback.
