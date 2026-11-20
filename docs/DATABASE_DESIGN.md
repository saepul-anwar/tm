# Desain Database

DDL lengkap ada di spec §7 dan diterapkan oleh `backend/src/main/resources/db/migration/V1__init.sql` (sumber kebenaran). MySQL 8.0, InnoDB, utf8mb4.

| Tabel | Fungsi | Kunci/indeks penting |
|---|---|---|
| `users` | Akun mahasiswa, `telegram_chat_id`, `timezone` | unik `email`, unik `telegram_chat_id` |
| `tasks` | Tugas; `deadline` UTC, `priority_rank` (kolom generated), `version` (optimistic lock) | `(user_id,status,deadline)`, `(status,deadline)` untuk scheduler |
| `reminder_settings` | 4 toggle reminder per user (1:1) | unik `user_id` |
| `notification_logs` | Klaim/riwayat kirim reminder | unik `(task_id,reminder_type,scheduled_at)` = pencegah duplikat; `(status,next_retry_at)`, `(status,locked_at)` |
| `telegram_link_tokens` | Token linking (hash SHA-256, TTL 10 mnt, sekali pakai) | unik `token_hash` |
| `refresh_tokens` | Refresh token (hash), rotasi dan pencabutan | unik `token_hash` |

## Aturan
- Semua tabel anak `ON DELETE CASCADE` ke `users`; `notification_logs` juga ke `tasks`.
- Perubahan skema hanya lewat migrasi Flyway baru (`V2__...`); jangan edit `V1`. Perubahan skema = risiko HIGH, perlu persetujuan.
- Retensi: `notification_logs` 90 hari; token kedaluwarsa dibersihkan job harian.
- Hasil `EXPLAIN` query kandidat scheduler: TODO (dicatat di Phase 6).
- ERD: [ERD.md](ERD.md).
