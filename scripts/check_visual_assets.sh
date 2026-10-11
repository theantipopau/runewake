#!/usr/bin/env bash
#
# Runewake visual-asset integrity check.
#
# Compiles and runs Client_Base/test/orsc/graphics/three/VisualAssetIntegrityTest.java
# against the built client jar, from cwd = Client_Base so the Cache/video
# relative asset paths resolve. It asserts the invariants of the shipped
# texture / model / material assets:
#
#   * every model registered by EntityHandler (objects, NPCs, spell effects)
#     exists in models.orsc and parses with geometry - a missing name renders
#     as a silent 1x1 placeholder (invisible object in the world);
#   * every material (face texture) reference on every model resolves within
#     the largest shipped texture set - the renderer clamps refs beyond the
#     active set to texture 0, so refs beyond every set are data loss;
#   * authentic textures (ids 3225..) all exist, square 64/128, with
#     boundWidth == width so the m_Hb shade flag is correct; custom textures
#     are contiguous, square, and likewise bound-consistent.
#
# Requires the client to be built first:
#   ant -f Client_Base/build.xml compile
#
# Usage: scripts/check_visual_assets.sh

set -eu

cd "$(dirname "$0")/.." || exit 1

CLIENT_JAR="Client_Base/Open_RSC_Client.jar"
TEST_SRC="Client_Base/test/orsc/graphics/three/VisualAssetIntegrityTest.java"
TEST_OUT="Client_Base/test/build"
CACHE_DIR="Client_Base/Cache/video"

if [ ! -f "$CLIENT_JAR" ]; then
	echo "FAIL: $CLIENT_JAR missing; build it first with:" >&2
	echo "  ant -f Client_Base/build.xml compile" >&2
	exit 1
fi

for asset in models.orsc Authentic_Sprites.orsc Custom_Sprites.osar; do
	if [ ! -f "$CACHE_DIR/$asset" ]; then
		echo "FAIL: $CACHE_DIR/$asset missing; the shipped cache is incomplete." >&2
		exit 1
	fi
done

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

# Absolutise: the test runs with cwd = Client_Base, so a repo-relative JAVA_BIN
# would break there.
case "$JAVA_BIN" in
	/*|[A-Za-z]:*) ;;
	*) JAVA_BIN="$(cd "$JAVA_BIN" && pwd)" ;;
esac

case "$(uname -s)" in
	MINGW*|MSYS*|CYGWIN*) CLASS_PATH_SEP=';' ;;
	*) CLASS_PATH_SEP=':' ;;
esac

rm -rf "$TEST_OUT"
mkdir -p "$TEST_OUT"

"$JAVA_BIN/javac" -encoding UTF-8 -cp "$CLIENT_JAR" -d "$TEST_OUT" "$TEST_SRC"

# VisualAssetIntegrityTest.main() calls System.exit(1) on failure, and resolves
# Cache/video relative to the working directory - run it inside Client_Base.
cd Client_Base
"$JAVA_BIN/java" -cp "Open_RSC_Client.jar${CLASS_PATH_SEP}test/build" orsc.graphics.three.VisualAssetIntegrityTest
