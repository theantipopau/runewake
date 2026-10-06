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
# It also fails on 7-digit literals. A colour is 6 or 8 hex digits, so a
# 7-digit token can never be one - and the colour scan used to match only its
# first 6 digits, which is how 0xFF00000 shipped as the party-menu hover
# colour: the text path reads only the low 24 bits, so it rendered as
# RGB(240,0,0) and nothing flagged it. Long runs (>= 9 digits, e.g. hash
# constants) still contribute their leading 8 digits, exactly as before.
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
		'0x[0-9a-fA-F]+' \
		Client_Base/src/orsc \
		Client_Base/src/com/openrsc/interfaces \
		PC_Client/src/orsc 2>/dev/null \
		| grep -v 'graphics/gui/Theme' \
		| awk -F: '
			{
				literal = tolower($NF)
				n = length(literal) - 2
				if (n < 6) next                    # 0x masks and flags, not colours
				if (n > 8) literal = substr(literal, 1, 10)  # long constants: leading 8, as before
				sub(/:[^:]*$/, ":" literal, $0)
				print
			}' \
		| sort -u
}

# Exactly-7-digit tokens: never a valid colour, and never visible to the
# colour scan above (which used to truncate them to 6 digits).
scan_malformed() {
	grep -rEo --include='*.java' \
		'0x[0-9a-fA-F]{7}([^0-9a-fA-F]|$)' \
		Client_Base/src/orsc \
		Client_Base/src/com/openrsc/interfaces \
		PC_Client/src/orsc 2>/dev/null \
		| grep -v 'graphics/gui/Theme' \
		| awk -F: '
			{
				literal = tolower($NF)
				sub(/[^0-9a-fA-F]$/, "", literal)
				sub(/:[^:]*$/, ":" literal, $0)
				print
			}' \
		| sort -u
}

CURRENT=$({ scan_literals; scan_malformed; } | sort -u)
MALFORMED=$(scan_malformed)

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

# Name the malformed-literal case explicitly: it is a typo, not a migration.
unclassified=$(printf '%s\n' "$MALFORMED" | awk -v base="$BASELINE" '
	BEGIN { while ((getline line < base) > 0) if (line !~ /^#/) known[line] = 1 }
	$0 != "" && !($0 in known) { print }')
if [ -n "$unclassified" ]; then	echo >&2
	echo "Malformed colour literal(s) - a colour is 0xRRGGBB or 0xAARRGGBB, so" >&2
	echo "7 hex digits can never be one:" >&2
	printf '%s\n' "$unclassified" >&2
	echo "0xFF00000 shipped as the party-menu hover colour and rendered as" >&2
	echo "RGB(240,0,0), because the text path reads only the low 24 bits. Fix" >&2
	echo "the typo; if it is a genuine numeric constant, classify it in the" >&2
	echo "baseline instead (step 3 above)." >&2
fi

echo >&2
echo "Classify each change: migrate to a Theme accessor, or update the" >&2
echo "baseline in the same commit with: scripts/check_theme_literals.sh --update-baseline" >&2
rm -f /tmp/runewake_baseline.$$ /tmp/runewake_current.$$ /tmp/runewake_diff.$$
exit 1
