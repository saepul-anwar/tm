# Desain Database

DDL lengkap ada di spec §7 dan diterapkan oleh `backend/src/main/resources/db/migration/V1__init.sql` (sumber kebenaran). MySQL 8.4 LTS, InnoDB, utf8mb4.

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
- `EXPLAIN` query kandidat scheduler: lihat bagian di bawah.
- ERD: [ERD.md](ERD.md).

## EXPLAIN query kandidat scheduler (Phase 6)
Query: `ReminderCandidateRepository` (satu query per jenis reminder; contoh di bawah untuk `H1`). Diukur di MySQL 8 nyata (angka di tabel diambil saat tes memakai tag floating `mysql:8`; tes sekarang berjalan di 8.4; TODO: verify angka pada 8.4) (Testcontainers, `ReminderCandidateRepositoryIT.explain_uses_a_range_index_on_tasks_and_not_a_full_scan`) dengan 6.000 task (6 user), 72.000 baris `notification_logs`, setelah `ANALYZE TABLE`:

| table | type | key | rows | Extra |
|---|---|---|---|---|
| `t` (tasks) | range | `ix_tasks_status_deadline` | 7 | Using index condition; Using filesort (hasil kecil) |
| `u` (users) | eq_ref | PRIMARY | 1 | Using where |
| `s` (reminder_settings) | ALL | - | 1 | hash join; tabel kecil (di data besar memakai `uk_reminder_settings_user`) |
| `n` (notification_logs) | eq_ref | `uk_nlogs_task_type_sched` | 1 | Not exists; Using index |

Tidak ada full scan pada `tasks` maupun `notification_logs`: jendela waktu ditulis langsung pada kolom `deadline` (`windowStart+offset < deadline <= now+offset`) sehingga indeks `(status, deadline)` terpakai, dan `NOT EXISTS` mengecek kunci unik per task.

Penyetelan yang dilakukan (hasil percobaan di MySQL 8):
- `status <> 'COMPLETED'` membuat optimizer memilih full index scan `ix_tasks_user_status_deadline` (~5.800 baris, temporary + filesort). Diganti `status IN ('TODO','IN_PROGRESS')` (himpunan yang sama menurut `ck_tasks_status`); status baru di masa depan harus ditambahkan ke query itu.
- Bila `notification_logs` sangat kecil, optimizer kadang memilih materialisasi `NOT EXISTS` (scan indeks log); pada log yang besar ia memakai probe `eq_ref` seperti tabel di atas. Karena MySQL merencanakan ulang tiap eksekusi, ini tidak perlu ditangani.
- User tanpa baris `reminder_settings` dianggap default (semua aktif): `LEFT JOIN` + `COALESCE(kolom, TRUE)`.
- `notification_logs` tidak punya indeks `created_at`; job pembersihan harian memindai tabel itu per batch. Dapat diterima untuk retensi 90 hari pada data per-user kecil; bila tabel membesar, tambahkan indeks lewat migrasi V2 (perlu persetujuan, risiko HIGH).
