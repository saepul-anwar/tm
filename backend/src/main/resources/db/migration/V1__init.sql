CREATE TABLE users (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  full_name             VARCHAR(100)  NOT NULL,
  email                 VARCHAR(254)  NOT NULL,
  password_hash         VARCHAR(100)  NOT NULL,          -- BCrypt
  email_verified_at     DATETIME(3)   NULL,              -- disiapkan untuk verifikasi email (belum dipakai)
  telegram_chat_id      BIGINT        NULL,
  telegram_connected_at DATETIME(3)   NULL,
  timezone              VARCHAR(64)   NOT NULL DEFAULT 'Asia/Jakarta',
  created_at            DATETIME(3)   NOT NULL,
  updated_at            DATETIME(3)   NOT NULL,
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_telegram_chat (telegram_chat_id)   -- satu chat hanya untuk satu akun
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tasks (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT       NOT NULL,
  title         VARCHAR(200) NOT NULL,
  description   TEXT         NULL,
  subject       VARCHAR(100) NULL,
  deadline      DATETIME(3)  NOT NULL,                  -- UTC
  priority      VARCHAR(10)  NOT NULL,                  -- LOW|MEDIUM|HIGH|URGENT
  priority_rank TINYINT AS (CASE priority WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2
                            WHEN 'HIGH' THEN 3 ELSE 4 END) STORED,   -- untuk sort prioritas
  status        VARCHAR(12)  NOT NULL DEFAULT 'TODO',   -- TODO|IN_PROGRESS|COMPLETED
  completed_at  DATETIME(3)  NULL,
  version       BIGINT       NOT NULL DEFAULT 0,        -- optimistic locking
  created_at    DATETIME(3)  NOT NULL,
  updated_at    DATETIME(3)  NOT NULL,
  CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
  CONSTRAINT ck_tasks_status   CHECK (status IN ('TODO','IN_PROGRESS','COMPLETED')),
  KEY ix_tasks_user_status_deadline (user_id, status, deadline),
  KEY ix_tasks_user_deadline        (user_id, deadline),
  KEY ix_tasks_user_priority        (user_id, priority_rank, deadline),
  KEY ix_tasks_user_subject         (user_id, subject),
  KEY ix_tasks_status_deadline      (status, deadline)   -- query scheduler
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reminder_settings (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id              BIGINT      NOT NULL,
  reminder_24_hours    BOOLEAN     NOT NULL DEFAULT TRUE,
  reminder_6_hours     BOOLEAN     NOT NULL DEFAULT TRUE,
  reminder_1_hour      BOOLEAN     NOT NULL DEFAULT TRUE,
  reminder_at_deadline BOOLEAN     NOT NULL DEFAULT TRUE,
  created_at           DATETIME(3) NOT NULL,
  updated_at           DATETIME(3) NOT NULL,
  UNIQUE KEY uk_reminder_settings_user (user_id),
  CONSTRAINT fk_reminder_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_logs (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT       NOT NULL,
  task_id       BIGINT       NOT NULL,
  reminder_type VARCHAR(16)  NOT NULL,   -- H24|H6|H1|AT_DEADLINE
  scheduled_at  DATETIME(3)  NOT NULL,   -- deadline dikurangi offset, UTC
  status        VARCHAR(12)  NOT NULL,   -- SENDING|SENT|RETRY|FAILED|SKIPPED
  attempts      INT          NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3)  NULL,
  locked_at     DATETIME(3)  NULL,
  sent_at       DATETIME(3)  NULL,
  error_message VARCHAR(500) NULL,       -- sudah disanitasi, tanpa token/URL bot
  created_at    DATETIME(3)  NOT NULL,
  updated_at    DATETIME(3)  NOT NULL,
  CONSTRAINT fk_nlogs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_nlogs_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
  UNIQUE KEY uk_nlogs_task_type_sched (task_id, reminder_type, scheduled_at),  -- pencegah duplikat
  KEY ix_nlogs_retry (status, next_retry_at),
  KEY ix_nlogs_stale (status, locked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE telegram_link_tokens (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT      NOT NULL,
  token_hash CHAR(64)    NOT NULL,       -- SHA-256 hex; token asli tidak disimpan
  expires_at DATETIME(3) NOT NULL,
  used_at    DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_tlt_hash (token_hash),
  KEY ix_tlt_user (user_id),
  CONSTRAINT fk_tlt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (               -- tambahan di luar spec: agar logout/rotasi nyata
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  token_hash  CHAR(64)    NOT NULL,
  expires_at  DATETIME(3) NOT NULL,         -- 7 hari
  revoked_at  DATETIME(3) NULL,
  created_at  DATETIME(3) NOT NULL,
  UNIQUE KEY uk_rt_hash (token_hash),
  KEY ix_rt_user (user_id),
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
