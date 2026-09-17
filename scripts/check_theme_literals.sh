#!/usr/bin/env bash
#
# Runewake theme-literal tripwire.
#
# Fails when raw colour literals (0xRRGGBB / 0xAARRGGBB) appear or disappear
# in the client UI layer without the baseline being updated in the same
# change. This protects the completed Theme migration: any new draw-layer
# literal must be consciously classified (migrate to a Theme accessor, or
# deliberately extend the baseline) instead of silently reintroducing
# hardcoded colours.
#
# Usage:
#   scripts/check_theme_literals.sh
#
# When it fails:
#   1. Review each reported file:literal pair.
#   2. If the colour should be themed, route it through a
#      Theme accessor (Client_Base/src/orsc/graphics/gui/Theme.java).
#   3. If the literal is intentional (engine internals, commented-out code,
#      debug overlays, ...), regenerate the baseline and include it in the
#      same commit:
#        scripts/check_theme_literals.sh --update-baseline
#
# Scan scope (the UI layer): Client_Base/src/orsc,
# Client_Base/src/com/openrsc/interfaces and PC_Client/src/orsc, excluding
# Theme.java itself, which is the one place new colour constants are expected.

set -u

cd "$(dirname "$0")/.." || exit 1

BASELINE="scripts/theme_literal_baseline.txt"
UPDATE=0
if [ "${1:-}" = "--update-baseline" ]; then
	UPDATE=1
fi

scan_literals() {
	grep -rEo --include='*.java' \
		'0x[0-9a-fA-F]{6}([0-9a-fA-F]{2})?' \
		Client_Base/src/orsc \
		Client_Base/src/com/openrsc/interfaces \
		PC_Client/src/orsc 2>/dev/null \
		| grep -v 'graphics/gui/Theme' \
		| awk -F: '{ literal = tolower($NF); sub(/:[^:]*$/, ":" literal, $0); print }' \
		| sort -u
}

CURRENT=$(scan_literals)

if [ "$UPDATE" -eq 1 ]; then
	{
		echo "# Runewake theme-literal baseline."
		echo "# Regenerated with: scripts/check_theme_literals.sh --update-baseline"
		echo "# Lines are <path>:<literal> pairs; see check_theme_literals.sh for scope."
		echo "#"
		echo "$CURRENT"
	} > "$BASELINE"
	echo "Baseline updated: $BASELINE ($(printf '%s\n' "$CURRENT" | wc -l | tr -d ' ') pairs)"
	exit 0
fi

if [ ! -f "$BASELINE" ]; then
	echo "FAIL: baseline missing. Generate it with: scripts/check_theme_literals.sh --update-baseline" >&2
	exit 1
fi

grep -v '^#' "$BASELINE" | sort -u > /tmp/runewake_baseline.$$
printf '%s\n' "$CURRENT" | sort -u > /tmp/runewake_current.$$

if diff -u /tmp/runewake_baseline.$$ /tmp/runewake_current.$$ > /tmp/runewake_diff.$$; then
	echo "OK: no unclassified colour-literal changes ($(printf '%s\n' "$CURRENT" | wc -l | tr -d ' ') pairs)."
	rm -f /tmp/runewake_baseline.$$ /tmp/runewake_current.$$ /tmp/runewake_diff.$$
	exit 0
fi

echo "FAIL: colour literals changed in the UI layer without a baseline update." >&2
echo >&2
echo "Added or changed literals (+) and removed ones (-):" >&2
cat /tmp/runewake_diff.$$ >&2
echo >&2
echo "Classify each change: migrate to a Theme accessor, or update the" >&2
echo "baseline in the same commit with: scripts/check_theme_literals.sh --update-baseline" >&2
rm -f /tmp/runewake_baseline.$$ /tmp/runewake_current.$$ /tmp/runewake_diff.$$
exit 1
