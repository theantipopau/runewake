#!/usr/bin/env bash
#
# RuneWake developer workflow - the single documented entry point.
#
# One command per job, so nobody has to remember the underlying Ant targets,
# guard scripts or JDK paths. Every verb delegates to the same implementations
# CI runs (.github/workflows/ci.yml and .gitlab-ci.yml), so a green local
# `check` means a green pipeline.
#
# Usage: scripts/dev.sh <command> [args]
#
#   check              Full validation: static guards, all three compiles,
#                      theme parity, pages build, world-boot smoke test.
#                      This is the "one command to validate".
#   build-server       Compile server core + plugins (server/core.jar, plugins.jar)
#   build-client       Compile the desktop client (Client_Base + PC_Client)
#                      into Client_Base/Open_RSC_Client.jar
#   build-launcher     Compile the PC launcher into PC_Launcher/OpenRSC.jar
#   build              All three compiles
#   run [world]        Run the game server in the foreground (Ctrl-C stops it).
#                      `world` is a config name from server/, default "default"
#                      (server/default.conf); ".conf" is optional.
#   release <version>  Build a reproducible release bundle with
#                      scripts/build_release.py (see docs/development/releasing.md).
#   help               This text
#
# Toolchain: uses $JAVA_HOME/$ANT_HOME when set, otherwise the vendored
# Portable_Windows JDK and Ant (which also work under Git Bash on Windows).
# No network access is required by any verb except `release` (none either,
# really - dependencies are vendored).

set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

note() { printf '%s\n' "$*"; }
die() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# --- toolchain ----------------------------------------------------------------

find_java_home() {
	if [ -n "${JAVA_HOME:-}" ] && { [ -x "$JAVA_HOME/bin/javac" ] || [ -x "$JAVA_HOME/bin/javac.exe" ]; }; then
		printf '%s\n' "$JAVA_HOME"
		return 0
	fi
	for d in Portable_Windows/*jdk*; do
		if [ -x "$d/bin/javac" ] || [ -x "$d/bin/javac.exe" ]; then
			printf '%s\n' "$d"
			return 0
		fi
	done
	if command -v javac >/dev/null 2>&1; then
		# Derive the home from the system compiler (bin/..).
		JAVAC=$(command -v javac)
		(cd "$(dirname "$JAVAC")/.." && pwd)
		return 0
	fi
	return 1
}

find_ant_home() {
	if [ -n "${ANT_HOME:-}" ] && [ -x "$ANT_HOME/bin/ant" ]; then
		printf '%s\n' "$ANT_HOME"
		return 0
	fi
	[ -x Portable_Windows/apache-ant-1.10.5/bin/ant ] || return 1
	printf '%s\n' "Portable_Windows/apache-ant-1.10.5"
}

setup_toolchain() {
	JAVA_HOME=$(find_java_home) || die "no JDK found; set JAVA_HOME or keep Portable_Windows/<jdk>"
	ANT_HOME=$(find_ant_home) || die "no Ant found; set ANT_HOME or keep Portable_Windows/apache-ant-1.10.5"
	case "$JAVA_HOME" in /*) ;; *) JAVA_HOME=$(cd "$JAVA_HOME" && pwd) || die "cannot enter JDK dir" ;; esac
	case "$ANT_HOME" in /*) ;; *) ANT_HOME=$(cd "$ANT_HOME" && pwd) || die "cannot enter Ant dir" ;; esac
	export JAVA_HOME ANT_HOME
	PATH="$JAVA_HOME/bin:$ANT_HOME/bin:$PATH"
	export PATH
	ANT="$ANT_HOME/bin/ant"
	[ -x "$ANT" ] || die "ant not executable: $ANT"
}

find_python() {
	local candidate path
	for candidate in python3 python; do
		path=$(command -v "$candidate" 2>/dev/null) || continue
		if "$path" -c "import sqlite3" >/dev/null 2>&1; then
			printf '%s\n' "$path"
			return 0
		fi
	done
	return 1
}

# --- commands -----------------------------------------------------------------

cmd_build_server() { setup_toolchain; "$ANT" -f server/build.xml compile_core compile_plugins; }
cmd_build_client() { setup_toolchain; "$ANT" -f Client_Base/build.xml compile; }
cmd_build_launcher() { setup_toolchain; "$ANT" -f PC_Launcher/build.xml compile; }

cmd_build() {
	cmd_build_server || exit 1
	cmd_build_client || exit 1
	cmd_build_launcher || exit 1
}

cmd_check() {
	setup_toolchain || exit 1
	note "== static guards =="
	bash scripts/check_theme_literals.sh || die "theme-literal guard"
	bash scripts/check_dependencies.sh || die "dependency guard"
	bash scripts/check_hosting_config.sh || die "hosting-config guard"
	note "== compiles =="
	"$ANT" -f server/build.xml compile_core compile_plugins || die "server compile"
	"$ANT" -f Client_Base/build.xml compile || die "client compile"
	"$ANT" -f PC_Launcher/build.xml compile || die "launcher compile"
	note "== theme parity (141 assertions) =="
	bash scripts/check_theme_parity.sh || die "theme parity"
	note "== pages =="
	bash scripts/build_pages.sh || die "pages build"
	note "== world boot smoke test =="
	bash scripts/check_boot.sh || die "boot smoke"
	note ""
	note "OK: full validation passed (guards, 3 compiles, parity, pages, boot)."
}

cmd_run() {
	setup_toolchain || exit 1
	conf=${1:-default}
	conf=${conf%.conf}
	note "Starting server ($conf.conf) - Ctrl-C to stop."
	exec "$ANT" -f server/build.xml runserver -DconfFile="$conf"
}

cmd_release() {
	[ -n "${1:-}" ] || die "usage: scripts/dev.sh release <version> (e.g. 0.1.2)"
	version=$1
	note "Building release $version - RELEASE.txt records HEAD, so build AFTER"
	note "the last release commit and from a clean tree (see docs/development/releasing.md)."
	PY_BIN=$(find_python) || die "python3 (or python) with sqlite3 is required to build a release"
	shift
	"$PY_BIN" scripts/build_release.py --version "$version" "$@"
}

usage() {
	sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'
}

# --- dispatch -----------------------------------------------------------------

case "${1:-help}" in
check) cmd_check ;;
build-server) cmd_build_server ;;
build-client) cmd_build_client ;;
build-launcher) cmd_build_launcher ;;
build) cmd_build ;;
run) shift; cmd_run "$@" ;;
release) shift; cmd_release "$@" ;;
help | --help | -h) usage ;;
*)
	printf 'Unknown command: %s\n\n' "$1" >&2
	usage >&2
	exit 2
	;;
esac
