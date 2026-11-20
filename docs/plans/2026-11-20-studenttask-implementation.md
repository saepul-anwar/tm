# StudentTask Implementation Plan

> **For agentic workers:** Implement this plan task-by-task with the `developer` agent, then validate with `test-engineer` and review with `code-reviewer`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membangun StudentTask: web app manajemen tugas kuliah dengan reminder Telegram otomatis tanpa duplikat.

**Architecture:** Dua aplikasi terpisah (Spring Boot backend, Next.js frontend) + MySQL, dijalankan Docker Compose. Backend berlapis per modul; reminder memakai polling scheduler yang mengklaim baris `notification_logs` lewat unique key (pendekatan B pada spec), mengirim di luar transaksi.

**Tech Stack:** Java 21, Spring Boot 3.x (GA stabil terbaru saat Task 1; catat versi di README), Spring Security/Data JPA/Validation, Flyway, Bucket4j, JUnit 5, Mockito, Testcontainers MySQL 8; Next.js (App Router) + TypeScript, Tailwind, shadcn/ui, Lucide, React Hook Form, Zod, Vitest + React Testing Library, Playwright; Docker Compose.

**Spec:** `docs/specs/2026-11-20-studenttask-design.md` (dibaca bersama plan ini; nomor bagian dirujuk sebagai §n).

## Global Constraints

- Backend Java 21, Maven, Spring Boot 3.x; constructor injection saja; paket `com.studenttask.{config,security,common,auth,user,task,notification,telegram,calendar,scheduler}`.
- Entity JPA tidak pernah keluar lewat REST; DTO + mapper. `ddl-auto=validate`; skema hanya lewat Flyway.
- Identitas user hanya dari security context; tidak ada `userId` di request; task milik user lain → 404.
- Respons: `{success,data,meta}` atau `{success:false,error:{code,message,details}}`. Kode: `VALIDATION_ERROR`400 `UNAUTHENTICATED`401 `FORBIDDEN`403 `NOT_FOUND`404 `CONFLICT`409 `RATE_LIMITED`429 `TELEGRAM_UNAVAILABLE`502.
- Waktu: UTC di DB (`DATETIME(3)`), JVM UTC; semua kode memakai `java.time.Clock` yang di-inject (tidak ada `Instant.now()` langsung); zona user dari `users.timezone` (default `Asia/Jakarta`).
- Password 8–72 byte (UTF-8), min 1 huruf + 1 angka, BCrypt. Access JWT 15 menit; refresh token 7 hari, dirotasi, disimpan sebagai SHA-256, cookie httpOnly+Secure+SameSite=Strict.
- Rate limit (nilai saya): register/login/refresh 10/menit per IP; `/telegram/connect` 5/menit per user; `/telegram/test` 3/menit per user.
- Task: title ≤200, description ≤5000, subject ≤100; page default 10, maks 50; kalender rentang ≤62 hari.
- Reminder: poll 60 dtk, catch-up window 10 mnt, batch 200/tick, backoff 1/5/15 mnt, maks 4 percobaan, `SENDING` macet >5 mnt diambil ulang, retensi log 90 hari; pesan plain text tanpa `parse_mode`.
- Rahasia hanya dari environment (`JWT_SECRET`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_BOT_USERNAME`, kredensial DB, `CORS_ALLOWED_ORIGINS`); start gagal bila kosong; `.env` tidak di-commit, `.env.example` hanya nama. Tidak ada rahasia, token, judul tugas, atau chat id utuh di log.
- Data uji sintetis saja. Tidak ada mock data pengganti integrasi backend di frontend. Telegram nyata tidak dipanggil di test otomatis.
- Cakupan ≥80% baris pada kode yang berubah; ≥90% pada logika reminder, otorisasi, kategori deadline. Test tidak boleh dilemahkan/dilewati agar hijau.
- Git: repo baru belum ada. `git init`, commit, push, dan PR **hanya setelah konfirmasi user**. Branch `feature/<slug>`, Conventional Commits; tidak commit ke `main` langsung (kecuali commit awal repo, yang juga perlu konfirmasi). Langkah "Commit" di bawah dijalankan hanya bila sudah dikonfirmasi.
- UI: label nav `Dashboard, My Tasks, Calendar, Completed, Settings`; teks isi Indonesia; zona waktu dari profil, bukan browser.

## Review Focus

Input/kondisi yang disiratkan spec tetapi paling mungkin lolos; tiap baris punya tes di task pemiliknya.
1. Password multibyte (mis. 30 emoji = >72 byte): ditolak `VALIDATION_ERROR`, tidak terpotong diam-diam oleh BCrypt. → Task 5.
2. Pencarian judul berisi `%` atau `_`: dianggap literal, tidak menjadi wildcard; judul/subject berisi emoji dan `<script>` tersimpan apa adanya. → Task 8.
3. Deadline tepat `now`, tepat tengah malam lokal, dan user di zona lain (mis. `America/New_York` pada hari pergantian DST): kategori benar; `deadline == now` bukan OVERDUE. → Task 10.
4. Chat Telegram sudah terhubung ke akun lain; token dipakai dua kali; user yang sudah terhubung menghubungkan ulang dengan chat baru. → Task 13.
5. Telegram 429 dengan `retry_after`, 403 (bot diblokir, tanpa retry), timeout, dan proses mati di tengah (`SENDING` macet) tidak menghasilkan duplikat atau kehilangan reminder. → Task 15.

---

## Phase 1 – Fondasi

### Task 1: Backend skeleton + migrasi V1

**Files:**
- Create: `backend/pom.xml`, `backend/mvnw*`, `backend/src/main/java/com/studenttask/StudentTaskApplication.java`, `backend/src/main/java/com/studenttask/config/{ClockConfig,AppProperties}.java`, `backend/src/main/resources/{application.yml,application-dev.yml,db/migration/V1__init.sql}`, `backend/src/test/java/com/studenttask/{MySqlTestBase,MigrationIT}.java`, `.gitignore`, `.editorconfig`, `.env.example`
- V1 berisi DDL §7 persis (6 tabel).

**Interfaces:**
- Produces: `@Bean Clock clock()` (UTC); `AppProperties` (`@ConfigurationProperties("app")` dengan `jwt.secret`, `telegram.*`, `reminder.*`, `cors.allowedOrigins`; `@Validated` + `@NotBlank` pada rahasia); `abstract class MySqlTestBase` (Testcontainers `mysql:8` singleton + `@DynamicPropertySource`) yang dipakai semua `*IT`.

- [ ] **Step 0:** Minta konfirmasi user untuk `git init -b main` di `task-reminder`; tanpa konfirmasi, lanjut tanpa git.
- [ ] **Step 1: Tes gagal** `MigrationIT`: `migration_creates_all_tables()` (users, tasks, reminder_settings, notification_logs, telegram_link_tokens, refresh_tokens ada), `notification_logs_rejects_duplicate_task_type_schedule()` (insert kedua dengan `(task_id,reminder_type,scheduled_at)` sama → `DuplicateKeyException`), `users_rejects_duplicate_email_and_chat_id()`, `deleting_user_cascades_tasks_and_logs()`, `app_fails_to_start_when_jwt_secret_blank()`.
- [ ] **Step 2:** `cd backend && ./mvnw -q test -Dtest=MigrationIT` → FAIL (belum ada proyek/migrasi).
- [ ] **Step 3:** Generate proyek Maven (Spring Initializr, Java 21), tambah dependensi Web, Security, JPA, Validation, Actuator, Flyway (`flyway-mysql`), MySQL driver, Bucket4j, Testcontainers; tulis V1 dari §7; `application.yml`: `spring.jpa.hibernate.ddl-auto=validate`, `hibernate.jdbc.time_zone=UTC`.
- [ ] **Step 4:** Jalankan `./mvnw -q test` → semua PASS.
- [ ] **Step 5: Commit** `chore: scaffold backend with flyway v1 schema`.

### Task 2: Frontend skeleton

**Files:**
- Create: `frontend/` (Next.js App Router, TS, Tailwind, shadcn/ui init, Vitest + RTL config, Playwright config), `frontend/src/app/{layout,page}.tsx`, `frontend/src/components/layout/AppShell.tsx`, `frontend/next.config.ts` (rewrite `/api/:path*` → `${BACKEND_URL}/api/:path*`), `frontend/src/lib/api.ts`
- Test: `frontend/src/components/layout/AppShell.test.tsx`

**Interfaces:**
- Produces: `AppShell({children})` — sidebar desktop (5 menu §4/nav), drawer di ponsel; `nav items` konstan `NAV_ITEMS: {label,href,icon}[]`.

- [ ] **Step 1: Tes gagal:** `renders_five_nav_items_in_order()` (Dashboard, My Tasks, Calendar, Completed, Settings), `mobile_menu_button_opens_drawer()`.
- [ ] **Step 2:** `cd frontend && npm test -- AppShell` → FAIL.
- [ ] **Step 3:** Implement `AppShell` + `NAV_ITEMS` (Lucide icons); rewrite proxy di `next.config.ts`; `BACKEND_URL` dari env.
- [ ] **Step 4:** `npm test` → PASS; `npm run lint && npm run build` → sukses.
- [ ] **Step 5: Commit** `chore: scaffold frontend with app shell`.

### Task 3: Docker Compose + dokumentasi struktur

**Files:**
- Create: `docker-compose.yml`, `backend/Dockerfile` (multi-stage, user non-root), `frontend/Dockerfile`, `docs/{PROJECT_PLAN,SYSTEM_ARCHITECTURE,DATABASE_DESIGN,ERD,API_SPECIFICATION,DEVELOPMENT_ROADMAP,DEPLOYMENT_GUIDE}.md`, `README.md`

- [ ] **Step 1:** `docker-compose.yml`: `mysql` (healthcheck, volume), `backend` (`depends_on: mysql: condition: service_healthy`, healthcheck ke `/actuator/health`), `frontend`; semua variabel dari `.env`.
- [ ] **Step 2:** Pecah spec §1–§13 menjadi 7 dokumen + README (jalankan lokal, variabel env berupa nama saja, versi yang dipakai, "tidak ada lupa-password di MVP"). Singkat, merujuk spec, tanpa menyalin seluruh isi.
- [ ] **Step 3: Verifikasi:** `cp .env.example .env` (isi nilai lokal sintetis) → `docker compose up -d --build` → `docker compose ps` menunjukkan 3 service healthy; `curl -fs localhost:8080/actuator/health` berisi `"status":"UP"`; `curl -fs localhost:3000` 200.
- [ ] **Step 4: Commit** `chore: add docker compose and project docs`. **Gerbang Phase 1:** kriteria §12 fase 1 terpenuhi.

---

## Phase 2 – Autentikasi

### Task 4: Fondasi API (respons, error, keamanan dasar)

**Files:**
- Create: `common/api/{ApiResponse,ApiError,FieldError,PageMeta}.java`, `common/error/{ErrorCode,AppException,GlobalExceptionHandler}.java`, `common/TokenHasher.java`, `security/{SecurityConfig,AuthenticatedUser,SecurityUtils,JsonAuthEntryPoint}.java`
- Test: `common/error/GlobalExceptionHandlerTest.java`, `common/TokenHasherTest.java`

**Interfaces:**
- Produces: `record ApiResponse<T>(boolean success, T data, ApiError error, PageMeta meta)` dengan `ok(T)`, `ok(T, PageMeta)`, `error(ErrorCode, String, List<FieldError>)`; `class AppException extends RuntimeException { AppException(ErrorCode, String) }`; `TokenHasher.sha256Hex(String): String`; `record AuthenticatedUser(long id, String email)`; `SecurityUtils.currentUserId(): long`.

- [ ] **Step 1: Tes gagal:** `validation_failure_maps_to_400_with_field_details()`, `unknown_exception_maps_to_500_without_stacktrace_in_body()`, `app_exception_maps_to_its_code_status()`, `sha256Hex("abc")` = `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='GlobalExceptionHandlerTest,TokenHasherTest'` → FAIL.
- [ ] **Step 3:** Implement; `SecurityConfig`: stateless, CSRF off untuk API Bearer, CORS dari `app.cors.allowedOrigins`, security headers, `/api/auth/register|login|refresh`, `/api/telegram/webhook`, `/actuator/health` publik, sisanya `authenticated()`; 401 berbentuk `ApiResponse`.
- [ ] **Step 4:** Test → PASS.
- [ ] **Step 5: Commit** `feat: add api response envelope and security base`.

### Task 5: Register, login, refresh, logout

**Files:**
- Create: `auth/{controller/AuthController,service/AuthService,service/JwtService,service/RefreshTokenService,dto/*,entity/RefreshToken,repository/RefreshTokenRepository}`, `user/{entity/User,repository/UserRepository}`, `user/entity/ReminderSettings` + repository, `security/{JwtAuthenticationFilter,RateLimitFilter}`
- Test: `auth/AuthServiceTest.java`, `auth/AuthControllerIT.java`, `security/RateLimitFilterTest.java`

**Interfaces:**
- Consumes: Task 4 `AppException`, `TokenHasher`, `Clock`.
- Produces: `AuthService.register(RegisterRequest): UserProfileResponse`; `login(LoginRequest): AuthTokens`; `refresh(String rawRefresh): AuthTokens`; `logout(String rawRefresh): void`; `revokeAll(long userId): void`; `record AuthTokens(String accessToken, long expiresInSeconds, String refreshToken)`; `JwtService.issue(long userId, String email): String` / `parse(String): AuthenticatedUser` (lempar `AppException(UNAUTHENTICATED)`); `RegisterRequest(fullName,email,password,confirmPassword)`; `LoginRequest(email,password)`.

- [ ] **Step 1: Tes gagal:** `register_creates_user_and_default_reminder_settings_all_true()`; `register_lowercases_email_and_rejects_duplicate_with_409()`; `register_rejects_mismatched_confirm()`; `register_rejects_password_over_72_bytes_even_if_under_72_chars()` (Review Focus 1: 30 emoji); `register_rejects_password_without_digit()`; `login_wrong_password_and_unknown_email_return_identical_401()`; `login_returns_access_token_and_httponly_refresh_cookie()`; `refresh_rotates_token_and_old_token_reuse_revokes_all_user_tokens()`; `logout_revokes_refresh_token()`; `expired_access_token_returns_401()`; `revokeAll_revokes_every_refresh_token_of_user()` (dipakai Task 12); `login_11th_request_per_minute_from_same_ip_returns_429()`; `password_never_appears_in_logs_or_responses()`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='Auth*,RateLimitFilterTest'` → FAIL.
- [ ] **Step 3:** Implement; `JwtService` HS256 dengan secret ≥256 bit dari env, klaim `sub`=user id; refresh token acak 32 byte base64url, simpan hash; kebocoran reuse → `UPDATE refresh_tokens SET revoked_at` untuk user; `RateLimitFilter` Bucket4j per IP pada 3 path.
- [ ] **Step 4:** Test → PASS.
- [ ] **Step 5: Commit** `feat: add jwt authentication with rotating refresh tokens`.

### Task 6: Alur auth frontend

**Files:**
- Create: `frontend/src/lib/{api.ts (extend),auth/AuthProvider.tsx,auth/schemas.ts}`, `frontend/src/app/{login,register}/page.tsx`, `frontend/src/middleware.ts`, `frontend/src/components/forms/{LoginForm,RegisterForm}.tsx`
- Test: `auth/schemas.test.ts`, `LoginForm.test.tsx`, `RegisterForm.test.tsx`, `AuthProvider.test.tsx`

**Interfaces:**
- Produces: `apiFetch<T>(path: string, init?: RequestInit): Promise<T>` (menyisipkan access token dari memori; saat 401 memanggil refresh sekali lalu mengulang; melempar `ApiError{code,message,details}`); `useAuth(): {status:'loading'|'authenticated'|'anonymous'; user; login(email,password); register(...); logout()}`; `loginSchema`, `registerSchema` (Zod, aturan sama dengan backend termasuk batas 72 byte).

- [ ] **Step 1: Tes gagal:** `loginSchema_rejects_invalid_email_and_empty_password()`; `registerSchema_rejects_mismatch_short_and_over_72_bytes_password()`; `LoginForm_shows_field_errors_and_blocks_submit()`; `LoginForm_shows_generic_error_on_401()`; `AuthProvider_restores_session_via_refresh_on_load()`; `apiFetch_retries_once_after_refresh_on_401()`; `logout_clears_token_and_redirects_to_login()`.
- [ ] **Step 2:** `npm test -- auth` → FAIL.
- [ ] **Step 3:** Implement; `middleware.ts` hanya mengalihkan halaman terproteksi → `/login` bila cookie refresh tidak ada (bukan otorisasi).
- [ ] **Step 4:** `npm test && npm run lint` → PASS. Verifikasi manual terhadap backend sungguhan di Compose: daftar → login → reload tetap login → logout.
- [ ] **Step 5: Commit** `feat: add frontend authentication flow`. **Gerbang Phase 2:** kriteria §12 fase 2.

---

## Phase 3 – Task

### Task 7: Task CRUD + kepemilikan

**Files:**
- Create: `task/{entity/{Task,Priority,TaskStatus},repository/TaskRepository,dto/{TaskRequest,TaskResponse,StatusRequest},mapper/TaskMapper,service/TaskService,controller/TaskController}`
- Test: `task/TaskServiceTest.java`, `task/TaskControllerIT.java`

**Interfaces:**
- Produces: `TaskService.create(long userId, TaskRequest): TaskResponse`; `get(long userId, long id)`; `update(long userId, long id, TaskRequest)`; `updateStatus(long userId, long id, TaskStatus)`; `delete(long userId, long id)`. `TaskRequest(title, description, subject, Instant deadline, Priority priority, TaskStatus status)`. `TaskResponse` memuat `deadline` (UTC), `completedAt`, `deadlineCategory` (diisi Task 10; sampai itu `null`). Repository selalu `findByIdAndUserId`.

- [ ] **Step 1: Tes gagal:** `create_requires_title_and_deadline()`; `create_allows_past_deadline()`; `user_b_gets_404_on_get_update_patch_delete_of_user_a_task()`; `status_completed_sets_completed_at_and_reopen_clears_it()`; `update_with_stale_version_returns_409()`; `title_over_200_chars_rejected()`; `delete_removes_task_and_cascades_notification_logs()`; `userId_in_request_body_is_ignored()`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='Task*'` → FAIL.
- [ ] **Step 3:** Implement; `@Version` pada entity; `deadline` diterima ISO offset dan disimpan UTC.
- [ ] **Step 4:** Test → PASS.
- [ ] **Step 5: Commit** `feat: add task crud with ownership checks`.

### Task 8: Daftar task: filter, sort, pagination

**Files:**
- Modify: `TaskController`, `TaskService`; Create: `task/{dto/TaskFilter,repository/TaskSpecifications}`; Test: `task/TaskListIT.java`

**Interfaces:**
- Produces: `TaskService.list(long userId, TaskFilter, Pageable): Page<TaskResponse>`; `TaskFilter(String q, String subject, TaskStatus status, Priority priority, Instant deadlineFrom, Instant deadlineTo)`; `GET /api/tasks?sort=deadline|priority|createdAt|completedAt&dir=asc|desc&page&size`; `TaskService.subjects(long userId): List<String>`; `GET /api/tasks/subjects`.

- [ ] **Step 1: Tes gagal:** `filters_by_each_field_and_combinations()`; `search_percent_and_underscore_are_literal()` (Review Focus 2); `title_with_emoji_and_script_tag_roundtrips_verbatim()`; `sort_by_priority_uses_urgent_first_then_deadline()`; `size_101_returns_400_and_default_size_is_10()`; `list_only_returns_own_tasks()`; `subjects_returns_distinct_sorted_own_subjects()`; `completed_page_query_sorts_by_completed_at_desc()`.
- [ ] **Step 2:** `./mvnw -q test -Dtest=TaskListIT` → FAIL.
- [ ] **Step 3:** Implement dengan JPA Specification; `q` di-escape (`\%`, `\_`) dan `LIKE ... ESCAPE '\\'`; sort prioritas memakai kolom `priority_rank` (mapping read-only).
- [ ] **Step 4:** Test → PASS.
- [ ] **Step 5: Commit** `feat: add task filtering sorting and pagination`.

### Task 9: Halaman task frontend

**Files:**
- Create: `frontend/src/app/{tasks/page,tasks/new/page,tasks/[id]/page,tasks/[id]/edit/page,completed/page}.tsx`, `frontend/src/features/tasks/{taskSchema.ts,useTasks.ts,TaskForm.tsx,TaskFilters.tsx,TaskList.tsx,TaskStatusSelect.tsx,DeleteTaskDialog.tsx}`, `frontend/src/components/ui/*` yang dibutuhkan
- Test: `taskSchema.test.ts`, `TaskForm.test.tsx`, `TaskFilters.test.tsx`, `TaskList.test.tsx`, `tasks.responsive.spec.ts` (Playwright)

**Interfaces:**
- Consumes: Task 6 `apiFetch`, `useAuth`.
- Produces: `taskSchema` (Zod; `deadlineDate`+`deadlineTime` + zona profil → ISO offset); `useTasks(filters): {data, meta, isLoading, error, refetch}`; komponen state: skeleton, empty, error.

- [ ] **Step 1: Tes gagal:** `taskSchema_requires_title_date_and_time()`; `TaskForm_submits_iso_deadline_in_profile_timezone()`; `TaskForm_edit_prefills_values()`; `TaskFilters_updates_query_params()`; `TaskList_shows_empty_and_error_states()`; `DeleteTaskDialog_requires_confirmation()`; `status_change_calls_patch_and_updates_list()`; Playwright `no_horizontal_scroll_at_375px_and_1280px()`.
- [ ] **Step 2:** `npm test -- tasks` → FAIL.
- [ ] **Step 3:** Implement; semua data dari backend sungguhan.
- [ ] **Step 4:** `npm test && npx playwright test tasks.responsive` → PASS; uji manual CRUD di Compose.
- [ ] **Step 5: Commit** `feat: add task management pages`. **Gerbang Phase 3.**

---

## Phase 4 – Dashboard

### Task 10: Kategori deadline + API dashboard

**Files:**
- Create: `task/service/DeadlineCategoryCalculator.java`, `task/entity/DeadlineCategory.java`, `task/{service/DashboardService,controller/DashboardController,dto/*}` (dashboard hidup di paket `task`, sesuai struktur paket spec)
- Modify: `TaskMapper` (isi `deadlineCategory`); Test: `DeadlineCategoryCalculatorTest`, `DashboardIT`

**Interfaces:**
- Produces: `enum DeadlineCategory {OVERDUE, DUE_TODAY, DUE_TOMORROW, UPCOMING, LATER}`; `DeadlineCategoryCalculator.categorize(Instant deadline, TaskStatus status, ZoneId zone, Instant now): DeadlineCategory` (`null` untuk COMPLETED); `DashboardService.summary(long userId): SummaryResponse(counts{total,todo,inProgress,completed,overdue,upcoming}, completionPercent, dueSoon[5], topPriority[5])`; `upcoming(long userId)` (7 hari, maks 50); `overdue(long userId)` (maks 50).

- [ ] **Step 1: Tes gagal:** `deadline_equal_to_now_is_not_overdue_but_one_ms_before_is()` (Review Focus 3); `completed_task_has_null_category()`; `local_midnight_boundary_today_vs_tomorrow_in_jakarta()`; `new_york_user_on_dst_change_day_gets_correct_category()`; `day_plus_8_is_later_and_day_plus_2_is_upcoming()`; `summary_counts_match_seeded_tasks_and_overdue_is_subset_of_open()`; `completion_percent_is_zero_when_no_tasks()`; `dashboard_excludes_other_users_tasks()`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='Deadline*,Dashboard*'` → FAIL.
- [ ] **Step 3:** Implement sesuai urutan prioritas §4D; hitungan di query agregat, bukan memuat semua task.
- [ ] **Step 4:** Test → PASS.
- [ ] **Step 5: Commit** `feat: add deadline categories and dashboard api`.

### Task 11: Dashboard frontend

**Files:**
- Create: `frontend/src/app/dashboard/page.tsx`, `frontend/src/features/dashboard/{useDashboard.ts,StatCards.tsx,ProgressCard.tsx,TaskMiniList.tsx}`; Test: `StatCards.test.tsx`, `DashboardPage.test.tsx`

- [ ] **Step 1: Tes gagal:** `StatCards_render_six_counts()`; `DashboardPage_shows_skeleton_then_data_then_error_state()`; `empty_dashboard_shows_empty_state_cta_to_create_task()`.
- [ ] **Step 2:** `npm test -- dashboard` → FAIL.
- [ ] **Step 3:** Implement (sederhana, tidak ramai; mengikuti §4B).
- [ ] **Step 4:** PASS; uji manual dengan data sintetis. **Gerbang Phase 4.**
- [ ] **Step 5: Commit** `feat: add dashboard page`.

---

## Phase 5 – Profil dan Telegram

### Task 12: Profile + reminder settings API

**Files:**
- Create: `user/{service/ProfileService,service/ReminderSettingsService,controller/ProfileController,controller/ReminderSettingsController,dto/*}`; Test: `ProfileServiceTest`, `ProfileIT`

**Interfaces:**
- Consumes: Task 5 `AuthService.revokeAll`.
- Produces: `ProfileService.get(long userId): ProfileResponse(fullName,email,timezone,TelegramStatus{connected,connectedAt})`; `update(long userId, UpdateProfileRequest): ProfileResponse` (`fullName?,email?,timezone?,currentPassword?,newPassword?`); `ReminderSettingsService.get/update(long userId, ReminderSettingsRequest(reminder24Hours,reminder6Hours,reminder1Hour,reminderAtDeadline))`.

- [ ] **Step 1: Tes gagal:** `email_change_requires_current_password()`; `new_password_requires_current_password_and_revokes_refresh_tokens()`; `duplicate_email_returns_409()`; `invalid_iana_timezone_returns_400()`; `settings_toggle_persists()`; `profile_never_returns_password_hash_or_chat_id()`.
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5: Commit** `feat: add profile and reminder settings api`.

### Task 13: Telegram client, linking, webhook/polling, disconnect, test

**Files:**
- Create: `telegram/{client/{TelegramClient,HttpTelegramClient,TelegramException},service/{TelegramLinkService,TelegramUpdateHandler,TelegramNotificationService},controller/TelegramController,runner/TelegramPollingRunner,runner/WebhookRegistrar,entity/TelegramLinkToken,repository/TelegramLinkTokenRepository,dto/*}`
- Test: `TelegramLinkServiceIT`, `TelegramControllerIT`, `HttpTelegramClientTest` (server HTTP palsu lokal, bukan Telegram nyata)

**Interfaces:**
- Produces: `interface TelegramClient { SendResult sendMessage(long chatId, String text); List<Update> getUpdates(long offset); void setWebhook(String url, String secret); void deleteWebhook(); }`; `TelegramException(Kind kind /*RETRYABLE|PERMANENT*/, Duration retryAfter, String sanitizedMessage)`; `TelegramLinkService.createLink(long userId): LinkResponse(String deepLink, Instant expiresAt)`; `handleStart(long chatId, String chatType, String token): LinkResult{LINKED,INVALID,EXPIRED,CHAT_TAKEN,NOT_PRIVATE}`; `TelegramNotificationService.sendTest(long userId)`; `disconnect(long userId)`.

- [ ] **Step 1: Tes gagal:** `webhook_without_or_with_wrong_secret_returns_401()`; `start_with_valid_token_links_chat_and_marks_used()`; `token_expires_after_10_minutes()`; `token_second_use_returns_INVALID()`; `chat_already_linked_to_other_user_returns_CHAT_TAKEN_and_does_not_change_either_user()` (Review Focus 4); `connect_again_invalidates_previous_token()`; `already_connected_user_relinks_to_new_chat_with_new_token()`; `group_chat_start_is_ignored()`; `start_without_token_replies_instructions()`; `stored_token_is_hash_not_raw()`; `disconnect_clears_chat_id()`; `test_when_not_connected_returns_409()`; `test_when_telegram_fails_returns_502_without_token_in_message()`; `bot_token_never_in_any_response_or_log()`; `connect_6th_per_minute_returns_429()`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='Telegram*,HttpTelegram*'` → FAIL.
- [ ] **Step 3:** Implement; token acak 32 byte base64url, TTL 10 mnt; `updates` diproses oleh satu `TelegramUpdateHandler` untuk webhook dan polling; `TelegramPollingRunner` aktif hanya bila `telegram.mode=polling` (memanggil `deleteWebhook` saat start); `WebhookRegistrar` aktif hanya bila `telegram.mode=webhook`; `HttpTelegramClient` memakai timeout connect/read eksplisit dan memetakan 429/5xx/timeout → RETRYABLE, 400/403 → PERMANENT; pesan error tidak memuat URL bot.
- [ ] **Step 4:** Test → PASS.
- [ ] **Step 5: Commit** `feat: add telegram linking and bot client`.

### Task 14: Halaman Settings (profil, password, timezone, Telegram, reminder)

**Files:**
- Create: `frontend/src/app/settings/page.tsx`, `frontend/src/features/settings/{ProfileForm.tsx,PasswordForm.tsx,TimezoneSelect.tsx,TelegramCard.tsx,ReminderToggles.tsx,useProfile.ts}`; Test: `TelegramCard.test.tsx`, `ProfileForm.test.tsx`, `ReminderToggles.test.tsx`

- [ ] **Step 1: Tes gagal:** `TelegramCard_shows_deeplink_and_countdown_after_connect()`; `TelegramCard_polls_profile_every_3s_and_shows_connected()`; `TelegramCard_offers_reconnect_after_expiry()`; `disconnect_asks_confirmation()`; `test_button_shows_toast_on_success_and_error()`; `ReminderToggles_send_four_booleans()`; `email_change_requires_current_password_field()`.
- [ ] **Step 2–4:** FAIL → implement → PASS. Verifikasi manual end-to-end dengan bot Telegram sungguhan milik user (mode polling lokal): connect → Start → terhubung → Test message sampai. Token bot disediakan user lewat `.env`, tidak ditulis ke repo.
- [ ] **Step 5: Commit** `feat: add settings page with telegram connection`. **Gerbang Phase 5.**

---

## Phase 6 – Reminder engine

### Task 15: Klaim, kirim, retry (inti anti-duplikat)

**Files:**
- Create: `notification/{entity/{NotificationLog,ReminderType,NotificationStatus},repository/NotificationLogRepository,service/{ReminderClaimService,ReminderDispatcher,ReminderMessageFormatter}}`
- Test: `ReminderClaimServiceIT`, `ReminderDispatcherTest`, `ReminderMessageFormatterTest`

**Interfaces:**
- Consumes: Task 13 `TelegramClient`, `TelegramException`; Task 12 settings.
- Produces: `enum ReminderType {H24(24h), H6(6h), H1(1h), AT_DEADLINE(0)}` dengan `Duration offset()`; `record ReminderCandidate(long taskId, long userId, ReminderType type, Instant scheduledAt)`; `ReminderClaimService.tryClaim(ReminderCandidate): Optional<Long>` (insert `SENDING` attempts=1; kunci ganda → empty); `ReminderClaimService.claimRetryable(Instant now, int limit): List<Long>` (UPDATE atomik `RETRY` jatuh tempo dan `SENDING` macet >5 mnt → `SENDING`, `attempts+1`, hanya `affected==1`); `ReminderDispatcher.dispatch(long logId): void` (baca ulang task/user, `SKIPPED` bila COMPLETED/hilang/Telegram putus/lewat window; kirim di luar transaksi; catat `SENT|RETRY|FAILED`); `ReminderMessageFormatter.format(Task, ZoneId): String` (template §15.3).

- [ ] **Step 1: Tes gagal:** `concurrent_claims_by_16_threads_yield_exactly_one_winner()` (MySQL nyata); `claim_retryable_by_two_workers_returns_row_to_only_one()`; `429_with_retry_after_schedules_next_retry_at_at_least_that_long()`; `403_marks_FAILED_without_retry()`; `timeout_backs_off_1_5_15_minutes_then_FAILED_on_4th_attempt()`; `stale_SENDING_older_than_5_minutes_is_reclaimed_once()`; `task_completed_between_claim_and_send_is_SKIPPED_and_nothing_sent()`; `telegram_disconnected_before_send_is_SKIPPED()`; `deleted_task_cascade_removes_pending_retry()`; `error_message_has_no_bot_token_or_url()`; `formatter_outputs_spec_template_with_WIB_for_jakarta_and_literal_title()`; `formatter_does_not_set_parse_mode()` (Review Focus 5 untuk semua yang relevan).
- [ ] **Step 2:** `./mvnw -q test -Dtest='Reminder*'` → FAIL.
- [ ] **Step 3:** Implement; transaksi hanya membungkus insert/update status (singkat); `sendMessage` dipanggil di luar `@Transactional`.
- [ ] **Step 4:** PASS.
- [ ] **Step 5: Commit** `feat: add reminder claim dispatch and retry`.

### Task 16: Scheduler, kandidat, pembersihan

**Files:**
- Create: `scheduler/{ReminderScheduler,LogCleanupJob}.java`, `notification/repository/ReminderCandidateRepository.java`; Test: `ReminderSchedulerIT`, `LogCleanupJobIT`

**Interfaces:**
- Produces: `ReminderCandidateRepository.findCandidates(ReminderType type, Instant windowStart, Instant now, int limit): List<ReminderCandidate>` (query §10 langkah 1, terindeks); `ReminderScheduler.tick(): void` (`@Scheduled(fixedDelayString="${app.reminder.poll-interval}")`: per jenis → kandidat → `tryClaim` → `dispatch`; lalu `claimRetryable` → `dispatch`); `LogCleanupJob.run()` (hapus log >90 hari dan token Telegram/refresh kedaluwarsa).

- [ ] **Step 1: Tes gagal (jam di-inject):** `sends_H24_H6_H1_and_AT_DEADLINE_at_their_times_once_each()`; `disabled_toggle_sends_nothing_for_that_type()`; `completed_task_never_selected()`; `user_without_telegram_never_selected()`; `task_created_with_deadline_in_3h_skips_H24_and_H6_sends_H1()`; `scheduled_time_older_than_10_minutes_is_not_sent_late()`; `deadline_moved_later_after_H1_sent_creates_new_reminder_for_new_schedule()` (§15.5); `reopened_completed_task_sends_only_within_window()`; `two_ticks_in_a_row_send_once()`; `two_scheduler_instances_ticking_concurrently_send_once()`; `batch_limit_200_per_tick_respected()`; `changing_user_timezone_does_not_change_schedule()`; `cleanup_deletes_only_old_rows()`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='ReminderScheduler*,LogCleanup*'` → FAIL.
- [ ] **Step 3:** Implement; periksa `EXPLAIN` query kandidat memakai `ix_tasks_status_deadline` dan catat hasilnya di `docs/DATABASE_DESIGN.md`.
- [ ] **Step 4:** PASS; uji manual: task dengan deadline +65 menit di akun sintetis → pesan H1 diterima sekali pada bot sungguhan.
- [ ] **Step 5: Commit** `feat: add reminder scheduler and cleanup`. **Gerbang Phase 6.**

---

## Phase 7 – Kalender

### Task 17: Calendar API

**Files:**
- Create: `calendar/{service/CalendarService,controller/CalendarController,dto/CalendarTaskResponse}`; Test: `CalendarIT`

**Interfaces:**
- Produces: `CalendarService.tasks(long userId, Instant from, Instant to): List<CalendarTaskResponse(id,title,subject,deadline,priority,status)>`; `GET /api/calendar/tasks?from&to`.

- [ ] **Step 1: Tes gagal:** `returns_only_own_tasks_in_range_inclusive_start_exclusive_end()`; `range_over_62_days_returns_400()`; `from_after_to_returns_400()`; `includes_completed_tasks()`.
- [ ] **Step 2–4:** FAIL → implement → PASS. **Step 5: Commit** `feat: add calendar api`.

### Task 18: Calendar frontend

**Files:**
- Create: `frontend/src/app/calendar/page.tsx`, `frontend/src/features/calendar/{CalendarView.tsx,useCalendarTasks.ts,TaskDrawer.tsx,priorityColor.ts}`; Test: `CalendarView.test.tsx`, `calendar.responsive.spec.ts`

- [ ] **Step 1: Tes gagal:** `month_week_day_switch_requests_matching_range_in_profile_timezone()`; `next_prev_navigation_refetches()`; `clicking_date_lists_tasks_of_that_day()`; `clicking_task_opens_drawer_with_detail()`; `priority_colors_are_distinct_and_have_legend()`; `works_at_375px_without_horizontal_scroll()`.
- [ ] **Step 2–4:** FAIL → implement (library kalender boleh dipakai bila menghemat usaha; ambil data hanya dari backend) → PASS. **Step 5: Commit** `feat: add calendar page`. **Gerbang Phase 7.**

---

## Phase 8 – Pengerasan dan deployment

### Task 19: Tes lintas-user dan keamanan

**Files:**
- Create: `backend/src/test/java/com/studenttask/security/{CrossUserAccessIT,EndpointAuthorizationIT,SecurityHeadersIT}.java`; `docs/security-review.md`

- [ ] **Step 1: Tes gagal/pembuktian:** `every_authenticated_endpoint_returns_401_without_token()` (daftar endpoint dari `RequestMappingHandlerMapping`, bukan daftar tulisan tangan); `user_b_cannot_read_or_change_user_a_tasks_calendar_dashboard_settings_logs()`; `jwt_with_wrong_signature_or_alg_none_rejected()`; `cors_rejects_unlisted_origin()`; `error_bodies_contain_no_stacktrace_or_sql()`; `sql_meta_chars_in_filters_are_inert()`.
- [ ] **Step 2:** Jalankan; perbaiki celah pada kode (bukan pada tes) bila ada. Jalankan `security-reviewer` agent atas seluruh auth, Telegram, dan reminder; tulis temuan (lokasi saja, tanpa rahasia) di `docs/security-review.md`.
- [ ] **Step 3: Commit** `test: add cross-user and security integration tests`.

### Task 20: Konfigurasi produksi + dokumen akhir

**Gerbang keputusan:** hosting (lokal/demo vs online) dijawab user sebelum task ini.

**Files:**
- Modify: `docker-compose.yml` (profil `prod`), `docs/DEPLOYMENT_GUIDE.md`, `README.md`; Bila online: Create `deploy/Caddyfile` (reverse proxy + TLS), konfigurasi `telegram.mode=webhook` + `PUBLIC_BASE_URL`

- [ ] **Step 1:** Profil `prod`: tanpa port DB terekspos, `restart: unless-stopped`, secret hanya dari env, `SPRING_PROFILES_ACTIVE=prod`, cookie `Secure`. Online: reverse proxy HTTPS, `WebhookRegistrar` memanggil `setWebhook` dengan `secret_token` saat start.
- [ ] **Step 2:** `DEPLOYMENT_GUIDE.md`: variabel env (nama saja), langkah start/stop, healthcheck, backup volume MySQL, rollback, kegagalan umum; perbarui semua dokumen yang berubah.
- [ ] **Step 3: Verifikasi akhir:** `./mvnw verify` dan `npm test && npx playwright test` hijau; laporan cakupan memenuhi target; `docker compose --profile prod up -d --build` dari nol berjalan; telusuri 12 kriteria penerimaan (§15.2) satu per satu dengan bukti perintah/tes.
- [ ] **Step 4: Commit** `chore: add production configuration and deployment guide`. **Gerbang Phase 8 = MVP selesai.**
