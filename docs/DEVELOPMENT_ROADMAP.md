# Roadmap Pengembangan

Sumber: [spec §12](specs/2026-11-20-studenttask-design.md); langkah rinci per task: [plan](plans/2026-11-20-studenttask-implementation.md). Bukti penerimaan: [ACCEPTANCE_REPORT](ACCEPTANCE_REPORT.md); riwayat: [CHANGELOG](../CHANGELOG.md).

| Fase | Isi | Selesai bila | Status |
|---|---|---|---|
| 1 Fondasi | Skeleton backend + frontend, Flyway V1, Docker Compose, dokumen | `docker compose up` menjalankan 3 service, backend sehat, migrasi sukses | Selesai |
| 2 Auth | Register, login, refresh, logout, rate limit, alur auth frontend | Daftar, login, tetap login setelah reload, logout mencabut sesi | Selesai |
| 3 Task | CRUD, filter, sort, pagination, halaman task | Semua filter bekerja; user B tidak bisa akses task user A | Selesai |
| 4 Dashboard | Summary, upcoming, overdue, kategori deadline | Angka cocok dengan data uji | Selesai |
| 5 Telegram | Profil, pengaturan reminder, linking, test | Terhubung ke bot sungguhan; test message sampai | Selesai (mode polling) |
| 6 Reminder | Scheduler, retry, anti-duplikat | Tes konkuren tanpa duplikat; reminder nyata diterima | Selesai |
| 7 Calendar | Bulan/minggu/hari | Navigasi dan klik tugas bekerja | Selesai (minggu = daftar per hari) |
| 8 Hardening | Tes keamanan, konfigurasi lokal, panduan deploy | 12 kriteria penerimaan (spec §15.2) | Selesai untuk LOKAL; online ditunda (lihat "Sebelum online" di [DEPLOYMENT_GUIDE](DEPLOYMENT_GUIDE.md)) |

## Berikutnya (di luar MVP, belum dijadwalkan)
Keputusan hosting online dan pekerjaan "Sebelum online"; lupa-password (butuh email); mode webhook Telegram yang diuji nyata; upgrade Spring Boot 4; tampilan minggu per jam.

Risiko dan mitigasi: spec §13.
