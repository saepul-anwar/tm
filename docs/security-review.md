# Catatan Tinjauan Keamanan

Hanya lokasi dan hasil; tidak ada rahasia atau data nyata. Semua data uji sintetis.

## 1. Task 19: tes lintas-user dan keamanan (Phase 8)

Semua tes ada di `backend/src/test/java/com/studenttask/security/` dan memakai MySQL Testcontainers (`MySqlTestBase`).

| Kelas tes | Yang dibuktikan |
|---|---|
| `EndpointAuthorizationIT` | Semua route diambil dari `RequestMappingHandlerMapping` (bukan daftar tulis tangan). Semua route di luar allow-list publik (register, login, refresh, logout, webhook Telegram, `/actuator/health`) menjawab 401 dalam envelope tanpa token, dengan token sampah/kedaluwarsa/non-Bearer. Allow-list diverifikasi sama dengan `SecurityConfig.PUBLIC_PATHS`: route publik baru membuat tes gagal sampai ditambahkan sengaja. HEAD/TRACE/method-override tidak melewati autentikasi. |
| `CrossUserAccessIT` | User B mendapat 404 (badan identik dengan id yang tidak ada) untuk get/put/patch/delete task user A; list, subjects, dashboard (summary/upcoming/overdue), kalender tidak memuat data A; settings dan profil B hanya mengubah B walau body berisi id/userId A; B tidak bisa disconnect/test Telegram A; enumerasi id berurutan dan varian path (slash, `%xx`, `+id`, `;x=1`, `%00`, `..`) tidak membocorkan; tidak ada parameter/body endpoint bernama `userId`/`ownerId` (diperiksa lewat refleksi). |
| `JwtAttackIT` | Kunci lain, `alg=none/None/NONE`, HS384/HS512 dengan kunci asli, header RS256/ES256, injeksi `jku`/`kid`/`crit`, payload diubah dengan tanda tangan lama, kedaluwarsa, `nbf` masa depan, issuer salah/kosong, tanpa exp/sub/iat, sub aneh, header 200 KB, header Authorization ganda, skema `bearer` huruf kecil, token user yang sudah dihapus: semuanya 401 envelope tanpa jejak tumpukan (atau, untuk user terhapus, tidak ada data dan tidak ada 500). |
| `SecurityHeadersIT` | nosniff, X-Frame-Options, Referrer-Policy, CSP, `Cache-Control: no-store` pada 200/400/401/403/404/405/415; HSTS pada HTTPS; respons login/refresh `no-store` dan cookie `HttpOnly; Secure; SameSite=Strict`; CORS menolak origin tak terdaftar (sederhana dan preflight), hanya origin terdaftar yang dijawab dengan credentials, tanpa wildcard. |
| `ErrorExposureIT` | Badan error 400/401/403/404/405/415/429/500 berupa envelope generik: tanpa jejak tumpukan, nama kelas, SQL, nama framework, path berkas. 500 dipicu dengan spy `TaskService` yang melempar pesan berisi SQL/path. |
| `InjectionInertIT` | Metakarakter SQL, wildcard `%`/`_`, null byte, emoji, string 10 KB di semua parameter filter, path variable, field JSON, login/profil: hanya 200/201/400/404, tidak pernah 500, tidak ada data user lain, error tidak memantulkan input mentah. `sort`/`dir` berupa allow-list. |
| `ActuatorExposureIT` | Hanya `/actuator/health` (hanya `status`, tanpa detail); `env`, `beans`, `heapdump`, `metrics`, `loggers`, dst. tidak terekspos (404, atau 401 tanpa token). |
| `RequestLimitsIT` | Server nyata (Tomcat): body > 128 KiB ditolak 413 sebelum autentikasi, body chunked dipotong, multipart ditolak, header > batas Tomcat ditolak. |
| `UserRateLimitIsolationIT` | Batas per-user (`/telegram/connect` 5/menit, `/telegram/test` 3/menit) tidak dibagi antar user pada IP yang sama dan tidak dibagi antar aksi. Tes rate limit lama (`RateLimitFilterTest`, `RateLimitBypassIT`, `UserRateLimiterTest`) tidak diubah. |

### Celah yang ditemukan dan diperbaiki

| # | Temuan | Dampak | Perbaikan minimal | Bukti |
|---|---|---|---|---|
| V1 | Tidak ada batas ukuran body request. Tomcat hanya membatasi form; JSON dibaca Jackson sampai 20 MB per string. | DoS memori oleh satu klien (juga tanpa token). | `security/RequestSizeLimitFilter` (128 KiB; 413 `PAYLOAD_TOO_LARGE` jika Content-Length lebih besar, body chunked dipotong saat dibaca), didaftarkan di `SecurityConfig`; `ErrorCode.PAYLOAD_TOO_LARGE`; `application.yml`: `server.tomcat.max-http-form-post-size`, `max-swallow-size`, `spring.servlet.multipart.enabled=false`. | `RequestLimitsIT`: merah (2 tes: 400 bukan 413) tanpa filter, hijau dengan filter. |
| V2 | Token akses yang masih berlaku milik user yang sudah dihapus menyebabkan HTTP 500 pada `GET /api/reminders/settings`, `POST /api/tasks`, `POST /api/telegram/connect` (pelanggaran FK `users`). Tidak ada kebocoran data dan tidak ada baris yatim (FK mencegah). | Respons 500 yang tidak perlu; tidak ada akses data. | `GlobalExceptionHandler.handleUnknown`: kegagalan MySQL 1452 (no referenced row) dipetakan ke 401 `UNAUTHENTICATED`; kegagalan integritas lain tetap 500. | `JwtAttackIT.token_for_a_deleted_user_...`: merah (500 pada 3 endpoint), hijau sesudah; `GlobalExceptionHandlerTest` (2 tes baru). |

Tidak ada celah yang menyangkut isolasi data antar user, penerimaan JWT palsu, atau paparan actuator/error.

### Perilaku terdokumentasi (bukan celah)

- Token akses bersifat stateless: berlaku sampai 15 menit setelah akun dihapus (tidak ada endpoint hapus akun; baca mengembalikan data kosong, `/me` dan `/profile` 401).
- Skema `Bearer` tidak peka huruf besar/kecil (RFC 7235). Pada header Authorization ganda, container memakai yang pertama.
- `sub` numerik pada token yang sah ditandatangani diterima (hanya server yang bisa menandatangani).
- CORS Spring membandingkan origin tanpa peka huruf dan mengabaikan slash akhir; browser selalu mengirim bentuk kanonik, respons memantulkan origin permintaan.
- `Accept` yang tidak bisa dipenuhi menghasilkan 406 tanpa badan; `GET /error` (terautentikasi) menjawab JSON bawaan Boot tanpa detail.
- Update profil memiliki rate limit sendiri (429 pada pengulangan cepat).

## 2. Risiko yang diterima dari tinjauan sebelumnya

Tiga tinjauan independen (read-only, `security-reviewer`) memakai penomoran F1..F9 masing-masing. ID di bawah diberi awalan tinjauan: **A** = Phase 2 (autentikasi), **B** = Phase 5 (profil dan Telegram), **C** = Phase 8 (seluruh bagian yang belum ditinjau).

| ID | Ringkasan | Keputusan pemilik | Status |
|---|---|---|---|
| A-F2 | Rate limit per-IP memakai `getRemoteAddr()`; di belakang reverse proxy semua user berbagi satu alamat. | Ditunda sampai aplikasi online. | Terbuka (wajib sebelum online) |
| A-F4 | Tidak ada pembatasan per akun saat login dan tidak ada daftar password umum. | Ditunda; ditanyakan lagi saat memutuskan online. | Terbuka |
| A-F6 | Refresh bersamaan dari dua tab memicu deteksi pemakaian ulang token dan mengeluarkan user. | Diterima (dampak: login ulang). | Diterima |
| A-F8 | Register mengembalikan 409 "email sudah terdaftar" (enumerasi email). | Diterima; rate limit sebagai penahan. | Diterima |
| A-F9 | Webhook Telegram publik harus memverifikasi header rahasia dengan perbandingan waktu konstan. | Ditindaklanjuti di Task 13. | Selesai |
| B-F5 | Token akses tetap berlaku sampai 15 menit setelah ganti password (hanya refresh token yang dicabut). | Diterima dan didokumentasikan di `docs/API_SPECIFICATION.md`. | Diterima |
| B-F7 | Mode default `polling` tanpa penanda eksplisit di produksi; dengan dua instance `getUpdates` bentrok. | Ditunda ke saat online (profil produksi). | Terbuka (wajib sebelum online) |
| B-F9 | Webhook tidak punya batas laju atau daftar IP; hanya rahasia header. | Ditunda ke saat online (di reverse proxy). | Terbuka (wajib sebelum online) |

Temuan yang disetujui dan sudah diperbaiki: A-F1, A-F3, A-F5, A-F7, A-F8 (bagian BCrypt di luar transaksi) dan B-F1, B-F2, B-F3, B-F4, B-F6, B-F8, masing-masing dengan tes yang gagal dulu.

Batasan yang sudah tercatat di komentar kode (diterima untuk MVP satu instance):

- Rate limit di memori (`RateLimitFilter`, `UserRateLimiter`) tidak dibagi antar instance; di belakang reverse proxy `getRemoteAddr()` adalah alamat proxy sehingga pembatasan per-IP harus ditinjau ulang (Task 20).
- `/api/auth/logout` publik dengan sengaja (hanya memakai cookie refresh, idempotent).
- JWT HS256 simetris dengan satu rahasia dari environment.

## 3. Review keamanan independen Phase 8

- **Penilai / tanggal:** agent `security-reviewer` (read-only), 2026-11-21. Hanya pembacaan kode; tidak ada alat otomatis (gitleaks, semgrep, osv-scanner) dan tidak ada eksploitasi.
- **Hasil:** PASS dengan temuan; tidak ada CRITICAL/HIGH, tidak ada IDOR, tidak ada injeksi SQL.
- **Cakupan:** otorisasi dan IDOR (task, dashboard, kalender, pengaturan reminder), internal JWT dan konfigurasi keamanan, pipeline reminder, migrasi V1, frontend (penyimpanan token, XSS, redirect, CSP, `npm audit`), dependensi dan Docker, kebocoran rahasia di file terlacak.
- **Tidak ditinjau / tidak bisa dijalankan:** `dependency:tree` (tanpa daftar dependensi terverifikasi), riwayat git, TLS dan reverse proxy (belum ada di repositori), kode komponen frontend baris demi baris.

| ID | Sev | Temuan | Keputusan | Status |
|---|---|---|---|---|
| C-F1 | MEDIUM | `mysql:8.0` sudah end-of-life. | Pemilik menyetujui `mysql:8.4` LTS. | Selesai (Task 20): Compose dan `MySqlTestBase` memakai 8.4 dipin digest; upgrade in-place dari volume 8.0 diverifikasi. |
| C-F2 | LOW | Spring Boot 3.5.16 mungkin di luar dukungan open-source; daftar dependensi belum terverifikasi. | Pemilik: tetap di 3.5. Upgrade ke Boot 4 hanya sebagai pekerjaan terpisah sebelum online; jalankan OSV-Scanner atau `dependency-check`. | Diterima / terbuka sebelum online |
| C-F3 | LOW | Tidak ada batas ukuran body JSON. | Diperbaiki (V1 di bagian 1). | Selesai |
| C-F4 | LOW | CSP frontend mengizinkan `'unsafe-inline'`; tidak ada HSTS. | CSP nonce dan HSTS dikerjakan sebelum online. | Terbuka sebelum online |
| C-F5 | LOW | JWT user yang dihapus tetap diterima ≤15 menit. | Tidak terjangkau (tidak ada endpoint hapus akun). Respons 500 yang menyertainya diperbaiki (V2). Tambahkan pengecekan di `/refresh` saat fitur hapus akun dibuat. | Diterima |
| C-F6 | LOW | Base image Docker tidak dipin ke digest. | Dipin di Task 20. | Selesai: semua base image (mysql, temurin jdk/jre-alpine, node:22-alpine) dipin ke digest. |
| C-F7 | LOW | `.env.example` tidak lengkap. | Dilengkapi di Task 20. | Selesai: daftar variabel lengkap diserahkan ke pemilik untuk disalin ke `.env.example` (agent tidak boleh menulis berkas itu). |
| C-F8 | INFO | Cookie `st_session` bisa dipalsukan (hanya UX; API tetap butuh JWT). | Diterima. | Diterima |
| C-F9 | INFO | `shadcn` (CLI) ada di `dependencies`; sumber 4 temuan moderate `npm audit`. | Dipindah ke `devDependencies`. | Selesai (commit `37a1354`) |

## 4. Task 20: konfigurasi lokal (infrastruktur)
Hosting ditetapkan LOKAL saja (2026-11-21); semua butir "wajib sebelum online" di atas tetap terbuka dan didaftar di [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md). Perubahan: MySQL 8.4 LTS dipin digest; semua base image dipin digest; Compose: port host dapat diatur tetapi tetap 127.0.0.1, `no-new-privileges`, backend/frontend `read_only` + `cap_drop: ALL`, batas memori/proses, rotasi log; skrip backup/restore tanpa rahasia di command line host. Belum ada pemindaian image atau alat otomatis (TODO: verify).
