# Spesifikasi API

Sumber: [spec §8](specs/2026-11-20-studenttask-design.md). Semua path di bawah `/api`; JWT `Authorization: Bearer` kecuali ditandai publik.
Saat ini yang ada: `/actuator/health` (di luar `/api`), endpoint `/api/auth/*` (Task 5), dan CRUD `/api/tasks[/{id}]` (Task 7), serta daftar/filter/sort/pagination `GET /api/tasks` dan `GET /api/tasks/subjects` (Task 8), dan `/api/dashboard/*` (Task 10). Skema field dan contoh (data sintetis) dilengkapi per fase bersamaan dengan implementasinya.

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
| GET | `/tasks` (filter, sort, page) | 3 | ada (Task 8) |
| POST | `/tasks` | 3 | ada (Task 7) |
| GET | `/tasks/{id}` | 3 | ada (Task 7) |
| PUT | `/tasks/{id}` | 3 | ada (Task 7) |
| PATCH | `/tasks/{id}/status` | 3 | ada (Task 7) |
| DELETE | `/tasks/{id}` | 3 | ada (Task 7) |
| GET | `/tasks/subjects` | 3 | ada (Task 8) |
| GET | `/dashboard/summary` | 4 | ada (Task 10) |
| GET | `/dashboard/upcoming` | 4 | ada (Task 10) |
| GET | `/dashboard/overdue` | 4 | ada (Task 10) |
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
Response `TaskResponse`: `id,title,description,subject,deadline(UTC),priority,status,completedAt,createdAt,updatedAt,version,deadlineCategory` (`deadlineCategory`: lihat Kategori deadline; `null` untuk task `COMPLETED`).
Kode: 201 (POST), 200 (GET/PUT/PATCH), 204 (DELETE), 400 `VALIDATION_ERROR` (termasuk enum/format tanggal salah dan `{id}` non-numerik), 401, 404 `NOT_FOUND` (tidak ada atau milik user lain, pesan identik), 409 `CONFLICT`. Menghapus task ikut menghapus `notification_logs` (FK cascade).

## Daftar task (Task 8)
`GET /tasks` -> 200 `{success,data:[TaskResponse],meta:{page,size,totalElements,totalPages}}`. Hanya task milik user pada token; `userId` di query diabaikan.
| Param | Aturan |
|---|---|
| `q` | judul mengandung teks (case-insensitive). `%`, `_`, dan `\` dianggap literal (bukan wildcard). Kosong/spasi = tanpa filter |
| `subject` | sama persis dengan mata kuliah |
| `status` | `TODO`, `IN_PROGRESS`, `COMPLETED` (huruf besar) |
| `priority` | `LOW`, `MEDIUM`, `HIGH`, `URGENT` (huruf besar) |
| `deadlineFrom` | ISO-8601 dengan `Z` atau offset (`+` harus di-encode `%2B`); **inklusif** (`deadline >= from`) |
| `deadlineTo` | ISO-8601 sama; **eksklusif** (`deadline < to`), jadi rentang berurutan tidak tumpang tindih |
| `sort` | `deadline` (default), `priority`, `createdAt`, `completedAt` (daftar putih; nilai lain -> 400) |
| `dir` | `asc` (default), `desc` |
| `page` | mulai 0, default 0 (negatif -> 400) |
| `size` | default 10, 1-50 (di luar rentang -> 400) |
Urutan: `sort=priority` memakai kolom `priority_rank` (LOW=1 .. URGENT=4), jadi `dir=desc` menaruh URGENT lebih dulu. Seri diurutkan `deadline` naik lalu `id` naik agar paging stabil. Halaman melebihi halaman terakhir -> `data: []` dengan `meta` tetap benar. Nilai enum/sort/dir/tanggal tidak valid -> 400 `VALIDATION_ERROR`. Halaman Completed: `status=COMPLETED&sort=completedAt&dir=desc`.
`GET /tasks/subjects` -> 200 `data: ["Biologi","Fisika",...]`: mata kuliah unik, non-null, terurut, milik user.

## Kategori deadline dan dashboard (Task 10)
**`deadlineCategory`** (di setiap `TaskResponse`: create/get/update/status/list dan daftar dashboard) dihitung saat request, tidak disimpan, memakai `Clock` server dan zona `users.timezone` (jika kosong/tidak valid -> `Asia/Jakarta`). Urutan prioritas: `null` bila `COMPLETED`; `OVERDUE` bila deadline < sekarang (ketat: deadline == sekarang **bukan** overdue); `DUE_TODAY` bila tanggal lokal deadline = tanggal lokal hari ini; `DUE_TOMORROW` bila = besok; `UPCOMING` bila setelahnya sampai sekarang + 7 x 24 jam (inklusif); selain itu `LATER`.

Definisi (semua relatif terhadap "sekarang" server, hanya task milik user pada token; "open" = status bukan `COMPLETED`):
- **overdue** = task open dengan deadline < sekarang. Irisan dari `todo + inProgress`, bukan status terpisah.
- **upcoming** = task open dengan deadline dalam `[sekarang, sekarang + 7 hari]` (kedua ujung inklusif). Catatan: ini jendela 7 hari penuh, bukan hanya kategori `UPCOMING` (task `DUE_TODAY`/`DUE_TOMORROW` ikut terhitung).
- **completionPercent** = `round(completed * 100 / total)` sebagai integer, dibulatkan ke atas pada .5 (half up; 2/7 = 28,57 -> 29); `0` bila `total = 0`.
- **dueSoon** = maks 5 task open dengan deadline terdekat yang belum lewat (deadline >= sekarang), urut deadline naik.
- **topPriority** = maks 5 task open, urut prioritas turun (`URGENT` > `HIGH` > `MEDIUM` > `LOW`), lalu deadline naik (task terlambat ikut bila prioritasnya tinggi), lalu id naik.
- Hitungan memakai query agregat; tidak ada paginasi/`meta` pada endpoint dashboard.

`GET /dashboard/summary` -> 200:
```json
{ "success": true, "data": {
    "counts": { "total": 7, "todo": 3, "inProgress": 2, "completed": 2, "overdue": 2, "upcoming": 2 },
    "completionPercent": 29,
    "dueSoon": [ /* TaskResponse, maks 5 */ ],
    "topPriority": [ /* TaskResponse, maks 5 */ ] } }
```
`GET /dashboard/upcoming` -> 200 `{success, data:[TaskResponse]}`: task open dengan deadline dalam `[sekarang, sekarang + 7 hari]`, urut deadline naik, maks 50.
`GET /dashboard/overdue` -> 200 `{success, data:[TaskResponse]}`: task open dengan deadline < sekarang, paling terlambat (deadline tertua) dahulu, maks 50.
Kode: 200, 401 `UNAUTHENTICATED` tanpa/dengan token tidak valid. Task milik user lain tidak pernah dihitung atau ditampilkan.
