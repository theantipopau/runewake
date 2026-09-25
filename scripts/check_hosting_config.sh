#!/usr/bin/env bash
# Validate the non-secret parts of a RuneWake hosting configuration.
#
# This script reads server/connections.conf and a world config, applies the
# same DB_* environment overrides as the Java server, and reports unsafe or
# incomplete deployment settings. It never prints passwords or environment
# secrets. It does not contact a database or start the game server.
#
# Usage:
#   bash scripts/check_hosting_config.sh [connections.conf] [world.conf]
#
# Optional overrides:
#   ALLOW_ROOT_DB=1              allow the root database account (not recommended)
#   ALLOW_EMPTY_DB_PASSWORD=1    allow an empty MySQL password (local testing only)

set -euo pipefail

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CONNECTIONS_FILE=${1:-"$ROOT/server/connections.conf"}
WORLD_FILE=${2:-"$ROOT/server/default.conf"}
FAILURES=0

error() {
  printf 'ERROR: %s\n' "$*" >&2
  FAILURES=$((FAILURES + 1))
}

notice() {
  printf 'NOTICE: %s\n' "$*"
}

# YMLReader deliberately supports a small, flat subset of the server config
# format. Strip comments only when they follow the value, then read one key.
read_file_setting() {
  local file=$1
  local wanted=$2
  awk -v wanted="$wanted" '
    {
      sub(/\r$/, "", $0)
      colon = index($0, ":")
      hash = index($0, "#")
      if (hash > 0 && (colon == 0 || hash < colon)) next
      if (hash > 0) $0 = substr($0, 1, hash - 1)
      colon = index($0, ":")
      if (colon <= 0) next
      key = substr($0, 1, colon - 1)
      value = substr($0, colon + 1)
      gsub(/^[ \t]+|[ \t]+$/, "", key)
      gsub(/^[ \t]+|[ \t]+$/, "", value)
      if (key == wanted) {
        print value
        found = 1
        exit
      }
    }
    END { if (!found) exit 1 }
  ' "$file"
}

get_setting() {
  local environment_name=$1
  local config_key=$2
  local config_file=$3
  local environment_value
  environment_value=$(printenv "$environment_name" 2>/dev/null || true)
  if [ -n "$environment_value" ]; then
    printf '%s' "$environment_value"
  else
    read_file_setting "$config_file" "$config_key" 2>/dev/null || true
  fi
}

require_file() {
  if [ ! -f "$1" ]; then
    error "configuration file not found: $1"
  fi
}

is_remote_host() {
  case "$1" in
    localhost|localhost:*|127.0.0.1|127.0.0.1:*|::1|::1:*) return 1 ;;
    *) return 0 ;;
  esac
}

require_file "$CONNECTIONS_FILE"
require_file "$WORLD_FILE"

if [ "$FAILURES" -ne 0 ]; then
  exit 1
fi

# Resolve settings in the same precedence order as ServerConfiguration.
db_type=$(get_setting DB_TYPE db_type "$CONNECTIONS_FILE")
db_name=$(get_setting DB_NAME db_name "$WORLD_FILE")
db_host=$(get_setting DB_HOST db_host "$CONNECTIONS_FILE")
db_user=$(get_setting DB_USER db_user "$CONNECTIONS_FILE")
db_pass=$(get_setting DB_PASS db_pass "$CONNECTIONS_FILE")
db_ssl_mode=$(get_setting DB_SSL_MODE db_ssl_mode "$CONNECTIONS_FILE")
db_connect_timeout=$(get_setting DB_CONNECT_TIMEOUT db_connect_timeout "$CONNECTIONS_FILE")
db_type=${db_type:-sqlite}
db_name=${db_name:-preservation}
db_host=${db_host:-localhost:3306}
db_connect_timeout=${db_connect_timeout:-10000}
if ! printf '%s' "$db_connect_timeout" | grep -Eq '^[0-9]+$' || [ "$db_connect_timeout" -lt 1 ]; then
  error "DB_CONNECT_TIMEOUT must be a positive integer"
fi
if [ -z "$db_ssl_mode" ]; then
  if is_remote_host "$db_host"; then
    db_ssl_mode=VERIFY_IDENTITY
  else
    db_ssl_mode=PREFERRED
  fi
fi
normalized_ssl_mode=$(printf '%s' "$db_ssl_mode" | tr '[:lower:]' '[:upper:]')
case "$normalized_ssl_mode" in
  DISABLED|PREFERRED|REQUIRED|VERIFY_CA|VERIFY_IDENTITY) ;;
  *) error "unsupported DB_SSL_MODE '$db_ssl_mode'" ;;
esac

normalized_type=$(printf '%s' "$db_type" | tr '[:lower:]' '[:upper:]')
case "$normalized_type" in
  SQLITE|1)
    if ! printf '%s' "$db_name" | grep -Eq '^[A-Za-z0-9_$-]+$'; then
      error "SQLite DB_NAME contains unsupported characters"
    fi
    if [ ! -f "$ROOT/server/inc/sqlite/$db_name.db" ]; then
      notice "SQLite database $db_name.db is not present yet; initialize it before first start"
    fi
    ;;
  MYSQL|0)
    if [ -z "$db_host" ] || printf '%s' "$db_host" | grep -q '://'; then
      error "MySQL DB_HOST must be a host or host:port, not a URL"
    fi
    if ! printf '%s' "$db_name" | grep -Eq '^[A-Za-z0-9_$-]+$'; then
      error "MySQL DB_NAME contains unsupported characters"
    fi
    if [ -z "$db_user" ]; then
      error "MySQL DB_USER is empty; use a dedicated application account"
    elif [ "$db_user" = "root" ] && [ "${ALLOW_ROOT_DB:-0}" != "1" ]; then
      error "MySQL DB_USER is root; use a dedicated application account"
    fi
    if [ -z "$db_pass" ] && [ "${ALLOW_EMPTY_DB_PASSWORD:-0}" != "1" ]; then
      error "MySQL DB_PASS is empty; set a secret or explicitly allow local testing"
    fi
    if [ "$db_user" = "root" ] && [ "$db_pass" = "root" ]; then
      error "refusing the sample root/root database credentials"
    fi

    if is_remote_host "$db_host"; then
      case "$normalized_ssl_mode" in
        VERIFY_IDENTITY) ;;
        VERIFY_CA|REQUIRED)
          notice "remote MySQL uses $normalized_ssl_mode; VERIFY_IDENTITY is preferred when the provider publishes a matching certificate"
          ;;
        *)
          error "remote MySQL must not use DB_SSL_MODE=$normalized_ssl_mode; use VERIFY_IDENTITY (or document a private-network exception)"
          ;;
      esac
    fi
    ;;
  *)
    error "unsupported DB_TYPE '$db_type' (expected sqlite or mysql)"
    ;;
esac

# Validate the public listener values without assuming a particular world file.
# The Java server currently reads listener values from the world file only;
# do not invent unrelated SERVER_* environment overrides in this preflight.
server_port=$(read_file_setting "$WORLD_FILE" server_port 2>/dev/null || true)
ws_server_port=$(read_file_setting "$WORLD_FILE" ws_server_port 2>/dev/null || true)
want_websockets=$(read_file_setting "$WORLD_FILE" want_feature_websockets 2>/dev/null || true)
server_port=${server_port:-43594}
ws_server_port=${ws_server_port:-43494}
want_websockets=${want_websockets:-true}

for port in "$server_port" "$ws_server_port"; do
  if ! printf '%s' "$port" | grep -Eq '^[0-9]+$' || [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
    error "listener port is outside 1-65535: $port"
  fi
done
if [ "$want_websockets" = "true" ] && [ "$server_port" = "$ws_server_port" ]; then
  error "server_port and ws_server_port must differ when websockets are enabled"
fi

compose_file="$ROOT/docker-compose.yml"
if [ -f "$compose_file" ]; then
  if grep -Eq '0\.0\.0\.0:3306' "$compose_file"; then
    error "docker-compose.yml publishes MariaDB on all host interfaces"
  fi
  if grep -Eq 'image:[[:space:]]*mariadb:latest' "$compose_file"; then
    error "docker-compose.yml uses the unpinned mariadb:latest image"
  fi
fi

for schema in "$ROOT/server/database/mysql/core.sql" "$ROOT/server/database/mysql/retro.sql"; do
  if [ -f "$schema" ] && grep -Eiq 'DROP[[:space:]]+TABLE' "$schema"; then
    notice "$(basename "$schema") is a destructive initialization script; use it only for an empty database"
  fi
done

if [ "$FAILURES" -ne 0 ]; then
  printf 'FAILED: %d hosting configuration error(s).\n' "$FAILURES" >&2
  exit 1
fi

printf 'OK: hosting configuration is structurally safe (DB_TYPE=%s, DB_NAME=%s, DB_SSL_MODE=%s, DB_CONNECT_TIMEOUT=%sms).\n' \
  "$normalized_type" "$db_name" "${normalized_ssl_mode:-PREFERRED}" "$db_connect_timeout"
