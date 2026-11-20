# Spesifikasi API

Sumber: [spec §8](specs/2026-11-20-studenttask-design.md). Semua path di bawah `/api`; JWT `Authorization: Bearer` kecuali ditandai publik.
Saat ini hanya `/actuator/health` (di luar `/api`) yang ada. Skema field dan contoh (data sintetis) dilengkapi per fase bersamaan dengan implementasinya.

## Format respons
Sukses: `{ "success": true, "data": ..., "meta": {page,size,totalElements,totalPages} }`.
Gagal: `{ "success": false, "error": { "code", "message", "details": [{field,message}] } }`.
Kode: `VALIDATION_ERROR` 400, `UNAUTHENTICATED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `CONFLICT` 409, `RATE_LIMITED` 429, `TELEGRAM_UNAVAILABLE` 502.
Task milik user lain -> 404. Tidak ada `userId` di request. `deadline` request: ISO-8601 dengan offset; response UTC (`Z`).

## Endpoint
| Method | Path | Fase | Status |
|---|---|---|---|
| GET | `/actuator/health` (publik) | 1 | ada |
| POST | `/auth/register` (publik) | 2 | belum diimplementasi |
| POST | `/auth/login` (publik) | 2 | belum diimplementasi |
| POST | `/auth/refresh` (cookie) | 2 | belum diimplementasi |
| POST | `/auth/logout` | 2 | belum diimplementasi |
| GET | `/auth/me` | 2 | belum diimplementasi |
| GET | `/tasks` (filter, sort, page) | 3 | belum diimplementasi |
| POST | `/tasks` | 3 | belum diimplementasi |
| GET | `/tasks/{id}` | 3 | belum diimplementasi |
| PUT | `/tasks/{id}` | 3 | belum diimplementasi |
| PATCH | `/tasks/{id}/status` | 3 | belum diimplementasi |
| DELETE | `/tasks/{id}` | 3 | belum diimplementasi |
| GET | `/tasks/subjects` | 3 | belum diimplementasi |
| GET | `/dashboard/summary` | 4 | belum diimplementasi |
| GET | `/dashboard/upcoming` | 4 | belum diimplementasi |
| GET | `/dashboard/overdue` | 4 | belum diimplementasi |
| GET | `/profile` | 5 | belum diimplementasi |
| PUT | `/profile` | 5 | belum diimplementasi |
| GET | `/reminders/settings` | 5 | belum diimplementasi |
| PUT | `/reminders/settings` | 5 | belum diimplementasi |
| POST | `/telegram/connect` | 5 | belum diimplementasi |
| POST | `/telegram/webhook` (publik, header rahasia) | 5 | belum diimplementasi |
| DELETE | `/telegram/disconnect` | 5 | belum diimplementasi |
| POST | `/telegram/test` | 5 | belum diimplementasi |
| GET | `/calendar/tasks?from&to` (maks 62 hari) | 7 | belum diimplementasi |
