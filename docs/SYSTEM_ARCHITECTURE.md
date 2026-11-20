# Arsitektur Sistem

Sumber: [spec §5, §9, §10](specs/2026-11-20-studenttask-design.md).

```
Browser ──> Next.js (frontend :3000)
              │ rewrite /api/* -> backend (same-origin, cookie refresh aman)
              ▼
        Spring Boot (backend :8080) ──JDBC──> MySQL 8.0
          │   ▲
  scheduler   └── Telegram webhook (prod) / getUpdates polling (dev)
          └──HTTPS──> api.telegram.org (sendMessage)
```

## Komponen
| Komponen | Folder | Catatan |
|---|---|---|
| Frontend | `frontend/` | Next.js App Router. `BACKEND_URL` dibaca saat **build** (rewrite dibekukan) dan runtime. Image: output `standalone`. |
| Backend | `backend/` | Paket `com.studenttask.{config,security,common,auth,user,task,notification,telegram,calendar,scheduler}`; controller -> service -> repository; DTO, bukan entity, di REST. |
| Database | service `mysql` | MySQL 8.0, skema hanya lewat Flyway (`ddl-auto=validate`), waktu UTC `DATETIME(3)`. |

## Keputusan utama
- **Reminder (pendekatan B)**: scheduler polling 60 dtk menghitung kandidat dari keadaan terkini, mengklaim lewat insert dengan unique key `(task_id, reminder_type, scheduled_at)`, mengirim di luar transaksi. Detail: spec §10.
- **Auth**: access JWT 15 mnt di memori; refresh token 7 hari httpOnly, dirotasi, disimpan sebagai hash. Detail: spec §4A.
- **Telegram**: linking lewat deep link `?start=<token>`; mode `polling` (dev) atau `webhook` (prod). Detail: spec §9.
- **Rahasia** hanya dari environment; aplikasi gagal start bila kosong.
- **Zona waktu**: UTC di DB/JVM; zona user (default `Asia/Jakarta`) dipakai di batas (kategori deadline, tampilan, pesan).
- Rate limit Bucket4j in-memory (satu instance; batasan diterima di MVP).

## Jaringan Compose
Hanya `frontend` (3000) dan `backend` (8080) dipublikasikan, keduanya terikat ke `127.0.0.1`. MySQL tidak dipublikasikan ke host.
