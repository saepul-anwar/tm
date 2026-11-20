# Spesifikasi API

Sumber: [spec §8](specs/2026-11-20-studenttask-design.md). Semua path di bawah `/api`; JWT `Authorization: Bearer` kecuali ditandai publik.
Saat ini yang ada: `/actuator/health` (di luar `/api`) dan endpoint `/api/auth/*` (Task 5). Skema field dan contoh (data sintetis) dilengkapi per fase bersamaan dengan implementasinya.

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

## Auth (Task 5)
- `POST /auth/register` body `{fullName,email,password,confirmPassword}` -> 201 `{id,fullName,email,timezone}`. Email di-lowercase, unik (409 `CONFLICT`). Password 8-72 **byte** UTF-8, min 1 huruf + 1 angka; `confirmPassword` harus sama (400 `VALIDATION_ERROR` dengan `details[].field`).
- `POST /auth/login` body `{email,password}` -> 200 `{accessToken,expiresIn}` (JWT HS256, 900 dtk) + cookie `refresh_token` (httpOnly, Secure, SameSite=Strict, Path=/api/auth, 7 hari). Email salah / password salah -> 401 `UNAUTHENTICATED` identik.
- `POST /auth/refresh` (cookie) -> 200 token baru + cookie baru; token lama sekali pakai. Pemakaian ulang token yang sudah dirotasi/dicabut -> 401 dan **semua** refresh token user dicabut.
- `POST /auth/logout` (cookie; publik agar tetap bisa dipakai setelah access token kedaluwarsa) -> 204, cookie dihapus, token dicabut; idempoten.
- `GET /auth/me` (Bearer) -> 200 `{id,fullName,email,timezone}`.
- Rate limit: register/login/refresh 10/menit per alamat remote (IPv6 dihitung per /64; path dinormalisasi: encoding, huruf besar, trailing slash tidak menghindari limit) -> 429 `RATE_LIMITED` + header `Retry-After`.
- Cek `Origin` pada `POST /auth/refresh` dan `POST /auth/logout`: jika header `Origin` ada, harus termasuk `app.cors.allowedOrigins`, jika tidak -> 403 `FORBIDDEN` (token tidak diputar/dicabut). Tanpa `Origin` (proxy same-origin, klien non-browser) tetap diizinkan.
- Flag `Secure` cookie: properti `app.auth.refresh-cookie-secure` (env `AUTH_REFRESH_COOKIE_SECURE`, default `true`). Setel `false` **hanya** untuk pengembangan lokal lewat http.
