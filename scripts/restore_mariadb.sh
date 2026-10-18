#!/usr/bin/env bash
# Restore a backup produced by scripts/backup_mariadb.sh into a (normally
# scratch) database, without putting the password in the process list.
# Companion to the backup script; backs the "restore drill" step in
# docs/HOSTING.md. Refuses to run against the name of the live game database
# unless RESTORE_ALLOW_LIVE=1 is set explicitly.
#
# Required environment:
#   MYSQL_DATABASE (restore target), MYSQL_USER, MYSQL_PASSWORD,
#   RESTORE_FILE (the .sql.gz written by the backup script)
# Optional:
#   MYSQL_HOST (127.0.0.1), MYSQL_PORT (3306), MYSQL_SSL_MODE, MYSQL_SSL_CA,
#   RESTORE_ALLOW_LIVE (must be exactly "1" to target the live database name)
#
# The live database name is read from LIVE_MYSQL_DATABASE (default "runewake").
# A checksum file is required: RESTORE_FILE.sha256 must exist and verify,
# otherwise the restore aborts before touching the database.

set -euo pipefail

: "${MYSQL_HOST:=127.0.0.1}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_DATABASE:?set MYSQL_DATABASE (restore target)}"
: "${MYSQL_USER:?set MYSQL_USER}"
: "${MYSQL_PASSWORD:?set MYSQL_PASSWORD}"
: "${RESTORE_FILE:?set RESTORE_FILE to the .sql.gz backup to restore}"
: "${LIVE_MYSQL_DATABASE:=runewake}"

case "$MYSQL_DATABASE" in
  (*[!A-Za-z0-9_$-]*)
    echo "ERROR: MYSQL_DATABASE contains unsupported characters" >&2
    exit 1
    ;;
esac

if [ ! -f "$RESTORE_FILE" ]; then
  echo "ERROR: RESTORE_FILE not found: $RESTORE_FILE" >&2
  exit 1
fi
if [ ! -f "$RESTORE_FILE.sha256" ]; then
  echo "ERROR: checksum file missing: $RESTORE_FILE.sha256 (refusing to restore unverified backup)" >&2
  exit 1
fi

command -v gunzip >/dev/null 2>&1 || {
  echo "ERROR: gunzip is not installed" >&2
  exit 1
}
command -v sha256sum >/dev/null 2>&1 || {
  echo "ERROR: sha256sum is not installed" >&2
  exit 1
}

if [ "$MYSQL_DATABASE" = "$LIVE_MYSQL_DATABASE" ] && [ "${RESTORE_ALLOW_LIVE:-}" != "1" ]; then
  echo "ERROR: refusing to restore over the live database '$LIVE_MYSQL_DATABASE' without RESTORE_ALLOW_LIVE=1" >&2
  exit 1
fi

# Verify the checksum in the backup directory (sha256sum -c resolves the
# filename recorded in the .sha256 file).
if ! (cd "$(dirname "$RESTORE_FILE")" && sha256sum -c "$(basename "$RESTORE_FILE").sha256"); then
  echo "ERROR: checksum verification failed for $RESTORE_FILE" >&2
  exit 1
fi

# Integrity-test the gzip stream before piping it into mysql.
if ! gzip -t "$RESTORE_FILE"; then
  echo "ERROR: $RESTORE_FILE is not a valid gzip stream" >&2
  exit 1
fi

command -v mysql >/dev/null 2>&1 || {
  echo "ERROR: mysql client is not installed" >&2
  exit 1
}

client_file=$(mktemp "${TMPDIR:-/tmp}/runewake-mysql-client.XXXXXX")
cleanup() {
  rm -f "$client_file"
}
trap cleanup EXIT HUP INT TERM

# mysql client option files avoid exposing the password through `ps`. Do not
# log this file or its contents.
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
)
if [ -n "${MYSQL_SSL_MODE:-}" ]; then
  args+=(--ssl-mode="$MYSQL_SSL_MODE")
fi
if [ -n "${MYSQL_SSL_CA:-}" ]; then
  args+=(--ssl-ca="$MYSQL_SSL_CA")
fi

if ! gunzip -c "$RESTORE_FILE" | mysql "${args[@]}" "$MYSQL_DATABASE"; then
  echo "ERROR: database restore failed" >&2
  exit 1
fi

printf 'Restore of %s into %s complete.\n' "$RESTORE_FILE" "$MYSQL_DATABASE"
