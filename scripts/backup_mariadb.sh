#!/usr/bin/env bash
# Create a private, transaction-consistent MariaDB backup without putting the
# password in the process list. The caller supplies an account with the
# privileges needed for its tables (normally SELECT/SHOW VIEW).
#
# Required environment:
#   MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD
# Optional:
#   MYSQL_HOST (127.0.0.1), MYSQL_PORT (3306), MYSQL_BACKUP_DIR (backups/mysql),
#   MYSQL_SSL_MODE, MYSQL_SSL_CA
#
# The output is written atomically, mode 0600, and accompanied by a SHA-256
# file. Test a restore regularly; this script is not a substitute for an
# off-host copy or a provider snapshot.

set -euo pipefail

: "${MYSQL_HOST:=127.0.0.1}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_DATABASE:?set MYSQL_DATABASE}"
: "${MYSQL_USER:?set MYSQL_USER}"
: "${MYSQL_PASSWORD:?set MYSQL_PASSWORD}"
: "${MYSQL_BACKUP_DIR:=backups/mysql}"

case "$MYSQL_DATABASE" in
  (*[!A-Za-z0-9_$-]*)
    echo "ERROR: MYSQL_DATABASE contains unsupported characters" >&2
    exit 1
    ;;
esac

command -v mysqldump >/dev/null 2>&1 || {
  echo "ERROR: mysqldump is not installed" >&2
  exit 1
}
command -v gzip >/dev/null 2>&1 || {
  echo "ERROR: gzip is not installed" >&2
  exit 1
}
command -v sha256sum >/dev/null 2>&1 || {
  echo "ERROR: sha256sum is not installed" >&2
  exit 1
}

umask 077
mkdir -p "$MYSQL_BACKUP_DIR"
chmod 700 "$MYSQL_BACKUP_DIR"
runewake_backup_dir="$MYSQL_BACKUP_DIR/$(date -u +%Y%m)"
mkdir -p "$runewake_backup_dir"
chmod 700 "$runewake_backup_dir"

client_file=$(mktemp "${TMPDIR:-/tmp}/runewake-mysql-client.XXXXXX")
backup_file="$runewake_backup_dir/$(date -u +%Y%m%dT%H%M%SZ)-${MYSQL_DATABASE}.sql.gz"
temporary_file="$backup_file.partial"
cleanup() {
  rm -f "$client_file" "$temporary_file"
}
trap cleanup EXIT HUP INT TERM

# mysql client option files avoid exposing the password through `ps`. Do not
# log this file or its contents. Passwords should be generated without newlines.
umask 077
{
  printf '[client]\n'
  printf 'host=%s\n' "$MYSQL_HOST"
  printf 'port=%s\n' "$MYSQL_PORT"
  printf 'user=%s\n' "$MYSQL_USER"
  printf 'password=%s\n' "$MYSQL_PASSWORD"
  printf 'protocol=tcp\n'
} > "$client_file"
chmod 600 "$client_file"

args=(
  --defaults-extra-file="$client_file"
  --single-transaction
  --quick
  --skip-lock-tables
  --no-tablespaces
  --routines
  --triggers
  --events
)
if [ -n "${MYSQL_SSL_MODE:-}" ]; then
  args+=(--ssl-mode="$MYSQL_SSL_MODE")
fi
if [ -n "${MYSQL_SSL_CA:-}" ]; then
  args+=(--ssl-ca="$MYSQL_SSL_CA")
fi

if ! mysqldump "${args[@]}" "$MYSQL_DATABASE" | gzip -c > "$temporary_file"; then
  echo "ERROR: database backup failed" >&2
  exit 1
fi
gzip -t "$temporary_file"
mv "$temporary_file" "$backup_file"
sha256sum "$backup_file" > "$backup_file.sha256"
chmod 600 "$backup_file" "$backup_file.sha256"

printf 'Backup written: %s\n' "$backup_file"
printf 'Checksum: %s\n' "$backup_file.sha256"
printf 'Copy this file and its checksum to an off-host location.\n'
