#!/usr/bin/env bash
# Dependency-drift guard.
#
# RuneWake vendors its jars (server/lib, PC_Client/lib) instead of using a
# package manager, so nothing notices when a build file references a jar that
# is not on disk, or when a jar is added to lib/ but never wired into a build.
# Both happened: server/build.xml referenced eleven jars that do not exist
# (disruptor-3.3.0, disruptor-3.3.5, xpp3_min, xstream-1.4.9 and seven
# netty-4.1.107 split jars) and only kept working because a "${lib}/*"
# wildcard quietly supplied the real ones, while log4j-slf4j18-impl sat on
# disk unreferenced.
#
# This check fails the build when a referenced jar is missing. Jars that are
# present but not named in a build file are reported as warnings (they are
# legitimate when a build relies on a "${lib}/*" classpath wildcard).
#
# Usage: bash scripts/check_dependencies.sh

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

status=0

# module-directory : build-file : lib-directory
MODULES="
server:server/build.xml:server/lib
PC_Client:Client_Base/build.xml:PC_Client/lib
"

for entry in $MODULES; do
    module=$(printf '%s' "$entry" | cut -d: -f1)
    build=$(printf '%s' "$entry" | cut -d: -f2)
    libdir=$(printf '%s' "$entry" | cut -d: -f3)

    if [ ! -f "$build" ]; then
        echo "ERROR: $module: build file not found: $build"
        status=1
        continue
    fi
    if [ ! -d "$libdir" ]; then
        echo "ERROR: $module: library directory not found: $libdir"
        status=1
        continue
    fi

    # Named jar references, ignoring the ones inside XML comments.
    # Note 1: match on "lib}/name.jar" rather than "${lib}/name.jar" - a "$"
    #          inside a grep basic-regex is a line-end anchor, so it never matches.
    # Note 2: comments are stripped with awk (a multi-line <!-- ... --> block
    #          cannot be handled line-by-line with sed), and "tr" is avoided
    #          because it would also translate literal x/0/1 in jar names.
    referenced=$(awk '
        /<!--/ {
            pre = $0
            sub(/<!--.*/, "", pre)
            rest = $0
            if (rest ~ /-->/) { sub(/.*-->/, "", rest); if (pre != "") print pre; if (rest != "") print rest; next }
            if (pre != "") print pre
            inc = 1
            next
        }
        inc == 1 {
            if ($0 ~ /-->/) { line = $0; sub(/.*-->/, "", line); inc = 0; if (line != "") print line }
            next
        }
        { print }
    ' "$build" \
        | grep -o 'lib}/[A-Za-z0-9._+-]*\.jar' \
        | sed 's|^lib}/||' \
        | sort -u)

    missing=0
    for jar in $referenced; do
        if [ ! -f "$libdir/$jar" ]; then
            echo "ERROR: $module: $build references $jar but $libdir/$jar does not exist"
            missing=1
        fi
    done
    [ "$missing" -eq 1 ] && status=1

    # Present but unnamed (wildcard classpaths are fine; just report them).
    present=$(cd "$libdir" && ls -1 ./*.jar 2>/dev/null | sed 's|^\./||' | sort -u)
    for jar in $present; do
        if ! printf '%s\n' $referenced | grep -qx "$jar"; then
            echo "WARN: $module: $libdir/$jar is present but not named in $build (classpath wildcard only)"
        fi
    done
done

if [ "$status" -ne 0 ]; then
    echo "FAILED: referenced dependencies are missing. See docs/DEPENDENCIES.md."
    exit 1
fi

echo "OK: every referenced dependency jar is present."
