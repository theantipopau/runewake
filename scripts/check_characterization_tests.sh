#!/usr/bin/env bash
#
# RuneWake characterization-test runner (Phase 4 of the modernisation charter).
#
# Compiles the plain-main test classes under server/test/ against the built
# server jar and executes each of them. There is no test framework in this
# project on purpose - these run with the same JDK 8 the server targets, the
# same way scripts/check_theme_parity.sh runs the client's parity guard.
#
# What is covered (each class pins CURRENT behaviour, not aspiration):
#   * PacketIoCharacterizationTest   - the three framing families, ISAAC
#     opcode encryption, client sniffing, and the resync-loop misdecode quirk
#   * IsaacCipherCharacterizationTest - the exact ISAAC keystream for a fixed
#     seed and encode/decode symmetry
#   * CryptoCharacterizationTest     - XTEA known-answer vector and the RSA
#     login-block round trip (512-bit modulus pinned as current weak behaviour)
#   * ServerConfigurationCharacterizationTest - shipped default.conf values,
#     remote-host VERIFY_IDENTITY default, websocket port-collision disable
#
# Requirements: server/core.jar (build with `ant -f server/build.xml
# compile_core`) and a JDK 8+. Fixture conf files are written by the script
# itself; nothing else is needed.
#
# Usage: scripts/check_characterization_tests.sh
#
# Output classes land in server/test/build (gitignored) and throwaway
# runtime files in dist/char_test_work (gitignored).

set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

# Windows-form repo root when available (Git Bash `pwd -W`): the tests run
# under java.exe, which cannot parse POSIX /c/... classpaths, and this
# repository path contains spaces. Falls back to POSIX for Linux CI.
REPO_ROOT="$(pwd)"
if W_ROOT="$(pwd -W 2>/dev/null)" && [ -n "$W_ROOT" ]; then
	REPO_ROOT="$W_ROOT"
fi

SERVER_JAR="server/core.jar"
TEST_SRC="server/test"
TEST_OUT="server/test/build"
WORK_DIR="dist/char_test_work"

note() { printf '%s\n' "$*"; }

if [ ! -f "$SERVER_JAR" ]; then
	echo "FAIL: $SERVER_JAR missing; build it first with:" >&2
	echo "  ant -f server/build.xml compile_core" >&2
	exit 1
fi

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

if ! JAVA_BIN="$(find_java_bin)"; then
	echo "FAIL: no JDK found; set JAVA_HOME or keep Portable_Windows/<jdk>/bin." >&2
	exit 1
fi
# Absolute: run_test executes java from other working directories.
case "$JAVA_BIN" in
	[A-Za-z]:*|/*) ;;
	*) JAVA_BIN="$REPO_ROOT/$JAVA_BIN" ;;
esac

case "$(uname -s)" in
MINGW*|MSYS*|CYGWIN*) CLASS_PATH_SEP=';' ;;
*) CLASS_PATH_SEP=':' ;;
esac

rm -rf "$TEST_OUT" "$WORK_DIR"
mkdir -p "$TEST_OUT" "$WORK_DIR"

# --- fixtures -----------------------------------------------------------
# The configuration test reads its files relative to CWD=server/, so its
# extra conf files are written into the work dir one level up from there.
cat > "$WORK_DIR/remote_host.conf" <<'EOF'
server_name: RuneWake
server_port: 43594
ws_server_port: 43494
db_host: db.example.com:3306
EOF

cat > "$WORK_DIR/portclash.conf" <<'EOF'
server_name: RuneWake
server_port: 44000
ws_server_port: 44000
EOF

# --- compile ------------------------------------------------------------
SOURCES="$(find "$TEST_SRC" -name '*.java' | sort)"
if [ -z "$SOURCES" ]; then
	echo "FAIL: no test sources under $TEST_SRC" >&2
	exit 1
fi

note "Compiling $(printf '%s\n' "$SOURCES" | wc -l) characterization test source(s)..."
if ! "$JAVA_BIN/javac" -encoding UTF-8 -cp "$SERVER_JAR" -d "$TEST_OUT" $SOURCES; then
	echo "FAIL: compilation failed." >&2
	exit 1
fi

# --- run ---------------------------------------------------------------
# Absolute classpath: the tests below run with different working directories.
CP="$REPO_ROOT/$SERVER_JAR$CLASS_PATH_SEP$REPO_ROOT/$TEST_OUT"

status=0
run_test() {
	local name="$1" cwd="$2" class="$3"
	# DB_* env vars outrank the conf files; a leaked value would make the
	# configuration assertions meaningless, so scrub them for every run.
	if (cd "$cwd" && env -u DB_TYPE -u DB_HOST -u DB_NAME -u DB_USER -u DB_PASS \
			-u DB_TABLE_PREFIX -u DB_SSL_MODE -u DB_CONNECT_TIMEOUT \
			"$JAVA_BIN/java" -cp "$CP" "$class"); then
		note "PASS $name"
	else
		note "FAIL $name"
		status=1
	fi
}

run_test "packet-io"        "."                       com.openrsc.server.net.PacketIoCharacterizationTest
run_test "isaac"            "."                       com.openrsc.server.net.rsc.IsaacCipherCharacterizationTest
# Crypto.init writes client.pem/server.pem into CWD when absent; the work dir
# keeps those throwaway keys out of the repository.
run_test "crypto"           "$WORK_DIR"               com.openrsc.server.net.rsc.CryptoCharacterizationTest
# initConfig reads connections.conf from CWD and its default file relative to
# it, so this one runs from server/ with the fixture confs one level up.
run_test "configuration"    "server"                  com.openrsc.server.ServerConfigurationCharacterizationTest

if [ "$status" -eq 0 ]; then
	note "OK: all characterization tests passed."
else
	note "FAIL: one or more characterization tests failed."
fi
exit "$status"
