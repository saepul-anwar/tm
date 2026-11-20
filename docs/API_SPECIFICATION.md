# Spesifikasi API

Sumber: [spec §8](specs/2026-11-20-studenttask-design.md). Semua path di bawah `/api`; JWT `Authorization: Bearer` kecuali ditandai publik.
Saat ini yang ada: `/actuator/health` (di luar `/api`), endpoint `/api/auth/*` (Task 5), dan CRUD `/api/tasks[/{id}]` (Task 7; daftar/filter dan `/tasks/subjects` menyusul di Task 8). Skema field dan contoh (data sintetis) dilengkapi per fase bersamaan dengan implementasinya.

## Format respons
Sukses: `{ "success": true, "data": ..., "meta": {page,size,totalElements,totalPages} }`.
Gagal: `{ "success": false, "error": { "code", "message", "details": [{field,message}] } }`.
Kode: `VALIDATION_ERROR` 400, `UNAUTHENTICATED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `CONFLICT` 409, `METHOD_NOT_ALLOWED` 405, `UNSUPPORTED_MEDIA_TYPE` 415, `NOT_ACCEPTABLE` 406, `RATE_LIMITED` 429, `TELEGRAM_UNAVAILABLE` 502.
Task milik user lain -> 404. Tidak ada `userId` di request. `deadline` request: ISO-8601 dengan offset; response UTC (`Z`).

## Endpoint
| Method | Path | Fase | Status |
|---|---|---|---|
| GET | `/actuator/health` (publik) | 1 | ada |
| POST | `/auth/register` (publik) | 2 | ada |
| POST | `/auth/login` (publik) | 2 | ada |
| POST | `/auth/refresh` (cookie) | 2 | ada |
| POST | `/auth/logout` (publik, cookie) | 2 | ada |
| GET | `/auth/me` | 2 | ada |
| GET | `/tasks` (filter, sort, page) | 3 | belum diimplementasi |
| POST | `/tasks` | 3 | ada (Task 7) |
| GET | `/tasks/{id}` | 3 | ada (Task 7) |
| PUT | `/tasks/{id}` | 3 | ada (Task 7) |
| PATCH | `/tasks/{id}/status` | 3 | ada (Task 7) |
| DELETE | `/tasks/{id}` | 3 | ada (Task 7) |
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

## Auth (Task 5)
- `POST /auth/register` body `{fullName,email,password,confirmPassword}` -> 201 `{id,fullName,email,timezone}`. Email di-lowercase, unik (409 `CONFLICT`). Password 8-72 **byte** UTF-8, min 1 huruf + 1 angka; `confirmPassword` harus sama (400 `VALIDATION_ERROR` dengan `details[].field`).
- `POST /auth/login` body `{email,password}` -> 200 `{accessToken,expiresIn}` (JWT HS256, 900 dtk) + cookie `refresh_token` (httpOnly, Secure, SameSite=Strict, Path=/api/auth, 7 hari). Email salah / password salah -> 401 `UNAUTHENTICATED` identik.
- `POST /auth/refresh` (cookie) -> 200 token baru + cookie baru; token lama sekali pakai. Pemakaian ulang token yang sudah dirotasi/dicabut -> 401 dan **semua** refresh token user dicabut.
- `POST /auth/logout` (cookie; publik agar tetap bisa dipakai setelah access token kedaluwarsa) -> 204, cookie dihapus, token dicabut; idempoten.
- `GET /auth/me` (Bearer) -> 200 `{id,fullName,email,timezone}`.
- Rate limit: register/login/refresh 10/menit per alamat remote (IPv6 dihitung per /64; path dinormalisasi: encoding, huruf besar, trailing slash tidak menghindari limit) -> 429 `RATE_LIMITED` + header `Retry-After`.
- Cek `Origin` pada `POST /auth/refresh` dan `POST /auth/logout`: jika header `Origin` ada, harus termasuk `app.cors.allowedOrigins`, jika tidak -> 403 `FORBIDDEN` (token tidak diputar/dicabut). Tanpa `Origin` (proxy same-origin, klien non-browser) tetap diizinkan.
- Flag `Secure` cookie: properti `app.auth.refresh-cookie-secure` (env `AUTH_REFRESH_COOKIE_SECURE`, default `true`). Setel `false` **hanya** untuk pengembangan lokal lewat http.

## Task CRUD (Task 7)
Request `POST`/`PUT /tasks`: `title` (wajib, di-trim, 1-200), `description?` (maks 5000), `subject?` (maks 100; kosong -> null), `deadline` (wajib, ISO-8601 dengan offset, mis. `2026-12-01T23:59:00+07:00`; tanggal lampau diperbolehkan), `priority` (wajib: `LOW|MEDIUM|HIGH|URGENT`), `status?` (`TODO|IN_PROGRESS|COMPLETED`; default `TODO` saat POST, pada PUT yang kosong mempertahankan status lama), `version?` (hanya PUT; bila ada dan tidak sama dengan versi saat ini -> 409 `CONFLICT`). Properti tak dikenal (mis. `userId`) diabaikan.
`PATCH /tasks/{id}/status`: `{ "status": "COMPLETED" }`. Masuk `COMPLETED` mengisi `completedAt`; keluar dari `COMPLETED` mengosongkannya.
Response `TaskResponse`: `id,title,description,subject,deadline(UTC),priority,status,completedAt,createdAt,updatedAt,version,deadlineCategory` (`deadlineCategory` selalu `null` sampai Task 10).
Kode: 201 (POST), 200 (GET/PUT/PATCH), 204 (DELETE), 400 `VALIDATION_ERROR` (termasuk enum/format tanggal salah dan `{id}` non-numerik), 401, 404 `NOT_FOUND` (tidak ada atau milik user lain, pesan identik), 409 `CONFLICT`. Menghapus task ikut menghapus `notification_logs` (FK cascade).
