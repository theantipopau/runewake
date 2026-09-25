#!/usr/bin/env bash
#
# Runewake theme-parity check.
#
# Compiles and runs Client_Base/test/orsc/graphics/gui/ThemeParityTest.java
# against the built client jar. It asserts the one hard invariant of the Theme
# migration:
#
#   * C_PREMIUM_THEME off  -> every themed accessor returns its exact inherited
#                             literal (classic rendering stays byte-identical);
#   * C_PREMIUM_THEME on   -> every themed role resolves to the intended
#                             premium token.
#
# This is the executable form of the "off path is unchanged" promise that
# scripts/check_theme_literals.sh can only enforce structurally.
#
# Requires the client to be built first:
#   ant -f Client_Base/build.xml compile
#
# Usage: scripts/check_theme_parity.sh

set -eu

cd "$(dirname "$0")/.." || exit 1

CLIENT_JAR="Client_Base/Open_RSC_Client.jar"
TEST_SRC="Client_Base/test/orsc/graphics/gui/ThemeParityTest.java"
TEST_OUT="Client_Base/test/build"

if [ ! -f "$CLIENT_JAR" ]; then
	echo "FAIL: $CLIENT_JAR missing; build it first with:" >&2
	echo "  ant -f Client_Base/build.xml compile" >&2
	exit 1
fi

# Prefer an explicit JAVA_HOME, then the JDK bundled with the repo.
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

case "$(uname -s)" in
	MINGW*|MSYS*|CYGWIN*) CLASS_PATH_SEP=';' ;;
	*) CLASS_PATH_SEP=':' ;;
esac

rm -rf "$TEST_OUT"
mkdir -p "$TEST_OUT"

# ThemeParityTest.main() calls System.exit(1) on failure, so no extra handling
# is needed here.
"$JAVA_BIN/javac" -encoding UTF-8 -cp "$CLIENT_JAR" -d "$TEST_OUT" "$TEST_SRC"
"$JAVA_BIN/java" -cp "$CLIENT_JAR$CLASS_PATH_SEP$TEST_OUT" orsc.graphics.gui.ThemeParityTest
