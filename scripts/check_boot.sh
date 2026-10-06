#!/usr/bin/env bash
#
# RuneWake world-boot smoke test.
#
# A clean compile is not enough to catch dependency regressions. The
# 2026-09-26 refresh left the server compiling perfectly and then dying during
# world load with `NoClassDefFoundError:
# org/apache/commons/io/input/CloseShieldInputStream`, because commons-compress
# 1.27+ declares commons-io at compile scope and only reaches it at runtime.
# Booting the world is the only check that catches that class of failure, so CI
# runs it instead of relying on someone booting a server by hand.
#
# It boots the world on SQLite (server/connections.conf, db_type: sqlite - no
# external services needed) and asserts that:
#
#   * /healthz  answers "ok"                (liveness probe)
#   * /status   serves the server-browse JSON
#   * /metrics  serves Prometheus exposition
#   * an unknown path returns 404
#   * the log records the game world listener and the startup banner
#
# server/inc/sqlite/*.db is gitignored, so a fresh checkout has no database and
# SqliteGameDatabaseConnection exits when the file is absent. The script seeds
# the configured database from the tracked schema (server/database/sqlite) the
# same way scripts/build_release.py seeds a release bundle.
#
# Requirements: server/core.jar + server/plugins.jar, a JDK, curl, and python3
# (only when the database has to be seeded).
# Build the jars first with:
#   ant -f server/build.xml compile
#
# Usage: scripts/check_boot.sh [world.conf]
#   BOOT_TIMEOUT=120 scripts/check_boot.sh preservation.conf
#
# The log is written to dist/boot_smoke.log (gitignored) and is echoed in full
# on failure.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

CONF=${1:-default.conf}
SERVER_DIR=server
LOG_FILE="dist/boot_smoke.log"
BOOT_TIMEOUT=${BOOT_TIMEOUT:-90}
SERVER_PID=""

note() { printf '%s\n' "$*"; }

stop_server() {
	[ -n "$SERVER_PID" ] || return 0
	kill "$SERVER_PID" 2>/dev/null
	for _ in 1 2 3 4 5; do
		if ! kill -0 "$SERVER_PID" 2>/dev/null; then
			SERVER_PID=""
			return 0
		fi
		sleep 1
	done
	kill -9 "$SERVER_PID" 2>/dev/null
	sleep 1
	SERVER_PID=""
	return 0
}

die() {
	printf 'FAIL: %s\n' "$*" >&2
	if [ -f "$LOG_FILE" ]; then
		printf '\n--- last 40 lines of %s ---\n' "$LOG_FILE" >&2
		tail -n 40 "$LOG_FILE" >&2
	fi
	stop_server
	exit 1
}

trap 'stop_server' EXIT

# --- prerequisites -----------------------------------------------------------

for jar in core.jar plugins.jar; do
	[ -f "$SERVER_DIR/$jar" ] ||
		die "$SERVER_DIR/$jar missing; build it first with: ant -f server/build.xml compile"
done
command -v curl >/dev/null 2>&1 || die "curl is required to query the health endpoints"

find_java_bin() {
	if [ -n "${JAVA_HOME:-}" ]; then
		if [ -x "$JAVA_HOME/bin/javac" ] || [ -x "$JAVA_HOME/bin/javac.exe" ]; then
			printf '%s\n' "$JAVA_HOME/bin"
			return 0
		fi
	fi
	for d in Portable_Windows/*jdk*/bin Portable_Windows/*jdk*/bin/*; do
		if [ -x "$d/javac" ] || [ -x "$d/javac.exe" ]; then
			printf '%s\n' "$d"
			return 0
		fi
	done
	return 1
}

JAVA_BIN=$(find_java_bin) || die "no JDK found; set JAVA_HOME or keep Portable_Windows/<jdk>/bin"
# The glob above yields a relative directory; the boot below runs from server/.
case "$JAVA_BIN" in
/*) ;;
*)
	JAVA_BIN=$(cd "$JAVA_BIN" && pwd) || die "cannot enter JDK dir: $JAVA_BIN"
	;;
esac

case "$(uname -s)" in
MINGW* | MSYS* | CYGWIN*) SEP=';' ;;
*) SEP=':' ;;
esac

# ws_server_port is what HttpRequestHandler binds (not server_port, which is the
# game TCP socket). Strip the inline comment the way YMLReader does.
CONF_FILE="$SERVER_DIR/$CONF"
[ -f "$CONF_FILE" ] || die "no such world config: $CONF_FILE"
PORT=$(awk '
	{
		sub(/\r$/, "", $0)
		colon = index($0, ":"); hash = index($0, "#")
		if (hash > 0 && (colon == 0 || hash < colon)) next
		if (hash > 0) $0 = substr($0, 1, hash - 1)
		colon = index($0, ":"); if (colon <= 0) next
		key = substr($0, 1, colon - 1); value = substr($0, colon + 1)
		gsub(/^[ \t]+|[ \t]+$/, "", key); gsub(/^[ \t]+|[ \t]+$/, "", value)
		if (key == "ws_server_port") { print value; found = 1; exit }
	}
	END { if (!found) exit 1 }
' "$CONF_FILE") || die "ws_server_port not found in $CONF_FILE"
[ -n "$PORT" ] || die "ws_server_port is empty in $CONF_FILE"

if curl -s -m 2 "http://127.0.0.1:$PORT/healthz" >/dev/null 2>&1; then
	die "port $PORT already answers /healthz; another server is running - stop it first"
fi

# --- database provisioning ---------------------------------------------------

# Fresh clones (every CI runner) have no server/inc/sqlite/<db_name>.db because
# *.db is gitignored, and the server refuses to start without it. Seed it from
# the checked-in schema instead of requiring a developer's local copy.
DB_NAME=$(awk '
	{
		sub(/\r$/, "", $0)
		colon = index($0, ":"); hash = index($0, "#")
		if (hash > 0 && (colon == 0 || hash < colon)) next
		if (hash > 0) $0 = substr($0, 1, hash - 1)
		colon = index($0, ":"); if (colon <= 0) next
		key = substr($0, 1, colon - 1); value = substr($0, colon + 1)
		gsub(/^[ \t]+|[ \t]+$/, "", key); gsub(/^[ \t]+|[ \t]+$/, "", value)
		if (key == "db_name") { print value; found = 1; exit }
	}
	END { if (!found) exit 1 }
' "$CONF_FILE") || DB_NAME=""

if [ -z "$DB_NAME" ]; then
	note "NOTE no db_name in $CONF_FILE; skipping database provisioning"
elif ! printf '%s' "$DB_NAME" | grep -Eq '^[A-Za-z0-9_$-]+$'; then
	die "db_name '$DB_NAME' contains unsupported characters"
else
	DB_FILE="$SERVER_DIR/inc/sqlite/$DB_NAME.db"
	if [ -f "$DB_FILE" ]; then
		note "OK  database $DB_FILE present"
	else
		note "Seeding absent $DB_FILE from server/database/sqlite (fresh checkout)"
		PY_BIN=""
		for candidate in python3 python; do
			candidate_path=$(command -v "$candidate" 2>/dev/null) || continue
			if "$candidate_path" -c "import sqlite3" >/dev/null 2>&1; then
				PY_BIN=$candidate_path
				break
			fi
		done
		[ -n "$PY_BIN" ] || die "python3 (or python) with sqlite3 is required to seed the absent SQLite database $DB_FILE"
		"$PY_BIN" - "$DB_NAME" <<'PY_SEED_EOF'
import pathlib
import sqlite3
import sys

root = pathlib.Path("server")
schema = root / "database" / "sqlite"
db_path = root / "inc" / "sqlite" / (sys.argv[1] + ".db")
db_path.parent.mkdir(parents=True, exist_ok=True)
connection = sqlite3.connect(str(db_path))
try:
    connection.executescript((schema / "core.sqlite").read_text(encoding="utf-8"))
    for addon in ("add_auctionhouse.sqlite", "add_bank_presets.sqlite", "add_clans.sqlite",
                  "add_equipment_tab.sqlite", "add_npc_kill_counting.sqlite"):
        connection.executescript((schema / "addons" / addon).read_text(encoding="utf-8"))
    connection.commit()
finally:
    connection.close()
PY_SEED_EOF
		seed_status=$?
		if [ "$seed_status" -ne 0 ]; then
			rm -f "$DB_FILE"
			die "failed to seed $DB_FILE from the checked-in schema (python exit $seed_status)"
		fi
		[ -f "$DB_FILE" ] || die "seed step reported success but $DB_FILE is missing"
		note "OK  seeded $DB_FILE from the checked-in schema"
	fi
fi

# --- boot --------------------------------------------------------------------

mkdir -p dist
: >"$LOG_FILE"

note "Booting $CONF (ws port $PORT); log: $LOG_FILE"
(
	cd "$SERVER_DIR" || exit 1
	exec "$JAVA_BIN/java" -Xmx512m -cp "core.jar${SEP}plugins.jar${SEP}lib/*" \
		com.openrsc.server.Server "$CONF"
) >"$LOG_FILE" 2>&1 &
SERVER_PID=$!

deadline=$((SECONDS + BOOT_TIMEOUT))
healthy=0
while [ "$SECONDS" -lt "$deadline" ]; do
	if ! kill -0 "$SERVER_PID" 2>/dev/null; then
		die "server process exited during startup"
	fi
	body=$(curl -s -m 2 "http://127.0.0.1:$PORT/healthz" 2>/dev/null || true)
	if [ "$(printf '%s' "$body" | tr -d '\r\n')" = "ok" ]; then
		healthy=1
		break
	fi
	sleep 1
done
[ "$healthy" -eq 1 ] || die "/healthz did not answer 'ok' within ${BOOT_TIMEOUT}s"
note "OK  /healthz -> ok"

# --- assertions --------------------------------------------------------------

status_json=$(curl -s -m 5 "http://127.0.0.1:$PORT/status" || true)
printf '%s' "$status_json" | grep -q '"serverName"' || die "/status has no serverName: $status_json"
printf '%s' "$status_json" | grep -q '"players"' || die "/status has no players count: $status_json"
printf '%s' "$status_json" | grep -q '"maxPlayers"' || die "/status has no maxPlayers: $status_json"
note "OK  /status  -> $status_json"

metrics=$(curl -s -m 5 "http://127.0.0.1:$PORT/metrics" || true)
printf '%s' "$metrics" | grep -q '# TYPE runewake_' || die "/metrics is not Prometheus exposition"
printf '%s' "$metrics" | grep -q 'runewake_players_online' || die "/metrics lacks runewake_players_online"
note "OK  /metrics -> $(printf '%s' "$metrics" | grep -c '^runewake_') gauges"

code=$(curl -s -o /dev/null -w '%{http_code}' -m 5 "http://127.0.0.1:$PORT/not-a-real-route" || true)
[ "$code" = "404" ] || die "unknown path returned ${code:-no response}, expected 404"
note "OK  unknown path -> 404"

grep -q "Game world is now online on TCP port" "$LOG_FILE" ||
	die "log never recorded the game world listener"
note "OK  game world listener logged"

grep -q "started in " "$LOG_FILE" || die "log never recorded the startup banner"
note "OK  startup banner logged"

stop_server
note "OK: world booted and answered on port $PORT (log: $LOG_FILE)"
exit 0
