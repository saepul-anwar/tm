#!/bin/sh
# Backup logis database StudentTask ke backups/studenttask-YYYYmmdd-HHMMSS.sql.
# Dijalankan dari mana saja; memakai service `mysql` dari Compose project yang aktif
# (ganti project dengan COMPOSE_PROJECT_NAME=...). Password dibaca DI DALAM container,
# jadi tidak muncul di command line host maupun output.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

umask 077
mkdir -p backups
OUT="backups/studenttask-$(date +%Y%m%d-%H%M%S).sql"
# Jangan pernah menimpa backup yang sudah ada (mis. file yang sedang dipulihkan): tunggu detik berikutnya.
while [ -e "$OUT" ]; do
  sleep 1
  OUT="backups/studenttask-$(date +%Y%m%d-%H%M%S).sql"
done
TMP="$OUT.partial"
trap 'rm -f "$TMP"' EXIT

docker compose exec -T mysql sh -c \
  'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
  > "$TMP"

# mysqldump menulis baris ini hanya jika dump selesai penuh.
if ! tail -n 3 "$TMP" | grep -q '^-- Dump completed'; then
  echo "Backup gagal: dump tidak lengkap (apakah service mysql berjalan?)." >&2
  exit 1
fi

mv "$TMP" "$OUT"
trap - EXIT
echo "Backup selesai: $OUT ($(wc -c < "$OUT" | tr -d ' ') byte)"
