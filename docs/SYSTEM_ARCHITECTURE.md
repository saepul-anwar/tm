# Arsitektur Sistem

Sumber: [spec §5, §9, §10](specs/2026-11-20-studenttask-design.md).

```
Browser ──> Next.js (frontend :3000)
              │ rewrite /api/* -> backend (same-origin, cookie refresh aman)
              ▼
        Spring Boot (backend :8080) ──JDBC──> MySQL 8.4
          │   ▲
  scheduler   └── Telegram webhook (prod) / getUpdates polling (dev)
          └──HTTPS──> api.telegram.org (sendMessage)
```

## Komponen
| Komponen | Folder | Catatan |
|---|---|---|
| Frontend | `frontend/` | Next.js App Router. `BACKEND_URL` dibaca saat **build** (rewrite dibekukan) dan runtime. Image: output `standalone`. |
| Backend | `backend/` | Paket `com.studenttask.{config,security,common,auth,user,task,notification,telegram,calendar,scheduler}`; controller -> service -> repository; DTO, bukan entity, di REST. |
| Database | service `mysql` | MySQL 8.4 LTS (image dipin digest), skema hanya lewat Flyway (`ddl-auto=validate`), waktu UTC `DATETIME(3)`. |

## Keputusan utama
- **Reminder (pendekatan B)**: scheduler polling 60 dtk menghitung kandidat dari keadaan terkini, mengklaim lewat insert dengan unique key `(task_id, reminder_type, scheduled_at)`, mengirim di luar transaksi. Detail: spec §10.
- **Auth**: access JWT 15 mnt di memori; refresh token 7 hari httpOnly, dirotasi, disimpan sebagai hash. Detail: spec §4A.
- **Telegram**: linking lewat deep link `?start=<token>`; mode `polling` (dev) atau `webhook` (prod). Detail: spec §9.
- **Rahasia** hanya dari environment; aplikasi gagal start bila kosong.
- **Zona waktu**: UTC di DB/JVM; zona user (default `Asia/Jakarta`) dipakai di batas (kategori deadline, tampilan, pesan).
- Rate limit Bucket4j in-memory (satu instance; batasan diterima di MVP).

## Jaringan Compose
Hanya `frontend` (`FRONTEND_PORT`, default 3000) dan `backend` (`BACKEND_PORT`, default 8080) dipublikasikan, keduanya terikat ke `127.0.0.1`. MySQL tidak dipublikasikan ke host. Service memakai `no-new-privileges`, batas memori/proses, rotasi log; backend dan frontend `read_only` dengan `tmpfs /tmp`. Tidak ada reverse proxy/HTTPS (hanya lokal; lihat [DEPLOYMENT_GUIDE](DEPLOYMENT_GUIDE.md)).

## Perbedaan dengan desain awal (yang benar-benar dibangun)
- Mode Telegram yang dipakai dan diverifikasi: `polling`. Mode `webhook` diimplementasikan (endpoint, `WebhookRegistrar`) tetapi belum dijalankan dengan Telegram sungguhan (TODO: verify).
- Tampilan kalender minggu berupa daftar per hari, bukan grid per jam.
- `GET /calendar/tasks` dibatasi 62 hari dan 1000 task per respons.
- Tabel `refresh_tokens` ditambahkan di luar spec awal (logout/rotasi nyata).
- Rate limit Bucket4j in-memory: per IP untuk auth, per user untuk profil dan Telegram, per chat untuk webhook.
- Filter ukuran body request 128 KiB (`RequestSizeLimitFilter`) dan pemetaan FK user terhapus ke 401 ditambahkan di Phase 8 (lihat [security-review](security-review.md)).
- MySQL 8.4 LTS menggantikan 8.0 (end-of-life); Spring Boot tetap 3.5.x.
