#!/bin/sh
# Pulihkan database StudentTask dari file backup .sql.
# DESTRUKTIF: isi database yang ada ditimpa. Pakai: scripts/restore-db.sh <file.sql> --yes
# Sebelum menimpa, dibuat backup pengaman (backups/studenttask-<waktu>.sql lewat backup-db.sh).
# Backend dihentikan selama restore lalu dinyalakan lagi. Password dibaca di dalam container.
set -eu

usage() {
  echo "Pemakaian: $0 <file-backup.sql> --yes" >&2
  echo "  --yes wajib sebagai konfirmasi eksplisit (menimpa seluruh isi database)." >&2
  exit 2
}

[ $# -eq 2 ] || usage
FILE=$1
[ "$2" = "--yes" ] || usage
[ -f "$FILE" ] || { echo "File tidak ditemukan: $FILE" >&2; exit 1; }
grep -q '^-- Dump completed' "$FILE" || { echo "File bukan dump lengkap (baris 'Dump completed' tidak ada)." >&2; exit 1; }

# Path absolut sebelum pindah direktori.
FILE=$(cd "$(dirname "$FILE")" && pwd)/$(basename "$FILE")
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

echo "1/4 Backup pengaman sebelum restore..."
"$ROOT/scripts/backup-db.sh"

echo "2/4 Menghentikan backend..."
docker compose stop backend

echo "3/4 Memulihkan dari $FILE ..."
docker compose exec -T mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
  < "$FILE"

echo "4/4 Menyalakan backend..."
docker compose start backend
echo "Restore selesai. Cek: docker compose ps"
