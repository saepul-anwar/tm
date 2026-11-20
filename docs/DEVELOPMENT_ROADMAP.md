# Roadmap Pengembangan

Sumber: [spec §12](specs/2026-11-20-studenttask-design.md); langkah rinci per task: [plan](plans/2026-11-20-studenttask-implementation.md). Tiap fase harus bisa dijalankan dan diuji sebelum lanjut.

| Fase | Isi | Selesai bila | Status |
|---|---|---|---|
| 1 Fondasi | Skeleton backend + frontend, Flyway V1, Docker Compose, dokumen | `docker compose up` menjalankan 3 service, backend sehat, migrasi sukses, halaman kosong terbuka | Task 1-3 selesai (menunggu review) |
| 2 Auth | Register, login, refresh, logout, rate limit, alur auth frontend | Daftar, login, tetap login setelah reload, logout mencabut sesi | belum |
| 3 Task | CRUD, filter, sort, pagination, halaman task | Semua filter bekerja; user B tidak bisa akses task user A | belum |
| 4 Dashboard | Summary, upcoming, overdue, kategori deadline | Angka cocok dengan data uji | belum |
| 5 Telegram | Profil, pengaturan reminder, linking, test | Terhubung ke bot sungguhan; test message sampai | belum |
| 6 Reminder | Scheduler, retry, anti-duplikat | Tes konkuren tanpa duplikat; reminder nyata diterima | belum |
| 7 Calendar | Bulan/minggu/hari | Navigasi dan klik tugas bekerja | belum |
| 8 Hardening | Tes keamanan, konfigurasi produksi, panduan deploy | 12 kriteria penerimaan (spec §15.2) terpenuhi | belum |

Risiko dan mitigasi: spec §13.
