#!/usr/bin/env bash
# Run a MySQL/MariaDB client command with its credentials in a private option
# file instead of -u/-p arguments. A password passed as an argument is visible
# to every local user in `ps`/Task Manager for the lifetime of the command, and
# ends up in shell history; the option file is mode 0600 and removed on exit.
#
# Usage:
#   bash scripts/mysql_client.sh mysql      [client args...]
#   bash scripts/mysql_client.sh mysqldump  [client args...]
#   bash scripts/mysql_client.sh mysqladmin [client args...]
#
# Credentials, first match wins:
#   MYSQL_USER / MYSQL_PASSWORD                   restricted account (preferred)
#   MARIADB_ROOT_USER / MARIADB_ROOT_PASSWORD     administrative account used by
#                                                 the legacy Makefile targets
# Optional: MYSQL_HOST (127.0.0.1), MYSQL_PORT (3306), MYSQL_SSL_MODE,
# MYSQL_SSL_CA.
#
# There is no interactive password prompt: a missing password is an error, so a
# make target or cron job fails fast instead of waiting on stdin.

set -euo pipefail

usage() {
  printf 'usage: %s <mysql|mysqldump|mysqladmin> [client args...]\n' "$(basename "$0")" >&2
}

client=${1:-}
case "$client" in
  mysql|mysqldump|mysqladmin) ;;
  *)
    usage
    exit 2
    ;;
esac
shift

: "${MYSQL_HOST:=127.0.0.1}"
: "${MYSQL_PORT:=3306}"
user=${MYSQL_USER:-${MARIADB_ROOT_USER:-root}}
password=${MYSQL_PASSWORD:-${MARIADB_ROOT_PASSWORD:-}}

if [ -z "$password" ]; then
  echo "ERROR: set MYSQL_PASSWORD (or MARIADB_ROOT_PASSWORD for the legacy admin targets)" >&2
  exit 1
fi

# A newline would terminate its line in the option file and silently change the
# credentials that the client uses.
case "$user$password" in
  *$'\n'*|*$'\r'*)
    echo "ERROR: credentials must not contain newlines" >&2
    exit 1
    ;;
esac

if ! command -v "$client" >/dev/null 2>&1; then
  echo "ERROR: $client is not installed" >&2
  exit 1
fi

client_file=$(mktemp "${TMPDIR:-/tmp}/runewake-mysql-client.XXXXXX")
cleanup() {
  rm -f "$client_file"
}
trap cleanup EXIT HUP INT TERM

umask 077
{
  printf '[client]\n'
  printf 'host=%s\n' "$MYSQL_HOST"
  printf 'port=%s\n' "$MYSQL_PORT"
  printf 'user=%s\n' "$user"
  printf 'password=%s\n' "$password"
  printf 'protocol=tcp\n'
} > "$client_file"
chmod 600 "$client_file"

args=(--defaults-extra-file="$client_file")
if [ -n "${MYSQL_SSL_MODE:-}" ]; then
  args+=(--ssl-mode="$MYSQL_SSL_MODE")
fi
if [ -n "${MYSQL_SSL_CA:-}" ]; then
  args+=(--ssl-ca="$MYSQL_SSL_CA")
fi

# Deliberately not exec: the EXIT trap must remove the credential file.
"$client" "${args[@]}" "$@"
