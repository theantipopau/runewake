#!/usr/bin/env bash
# Build the static GitHub Pages tree without changing the working branch.
#
# Source layout:
#   web/site/           -> published root
#   web/server-browser/ -> published /server-browser/
#
# The source page uses ../server-browser/ because it lives under web/site in
# the repository. GitHub Pages serves the landing page from the branch root,
# so the staged copy rewrites that link to server-browser/.
#
# Usage:
#   bash scripts/build_pages.sh              # validate in a temporary tree
#   bash scripts/build_pages.sh build/pages  # stage into a given directory

set -euo pipefail

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUTPUT_DIR=${1:-}
TEMP_TREE=0
if [ -n "$OUTPUT_DIR" ]; then
  case "$OUTPUT_DIR" in
    /*) STAGE=$OUTPUT_DIR ;;
    *) STAGE=$ROOT/$OUTPUT_DIR ;;
  esac
  if [ -e "$STAGE" ] && [ -n "$(find "$STAGE" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]; then
    echo "ERROR: output directory is not empty: ${STAGE#$ROOT/}" >&2
    exit 1
  fi
  mkdir -p "$STAGE"
else
  STAGE=$(mktemp -d "${TMPDIR:-/tmp}/runewake-pages.XXXXXX")
  TEMP_TREE=1
fi
cleanup() {
  if [ "$TEMP_TREE" -eq 1 ]; then
    rm -rf "$STAGE"
  fi
}
trap cleanup EXIT

required=(
  "$ROOT/web/site/index.html"
  "$ROOT/web/site/README.md"
  "$ROOT/web/site/assets/favicon-512.png"
  "$ROOT/web/site/assets/frame-0120.jpg"
  "$ROOT/web/site/assets/frame-0221.jpg"
  "$ROOT/web/server-browser/index.html"
  "$ROOT/web/server-browser/servers.json"
)
for file in "${required[@]}"; do
  if [ ! -f "$file" ]; then
    echo "ERROR: required Pages source is missing: ${file#$ROOT/}" >&2
    exit 1
  fi
done

mkdir -p "$STAGE/server-browser"
cp -R "$ROOT/web/site/." "$STAGE/"
cp -R "$ROOT/web/server-browser/." "$STAGE/server-browser/"

# Rewrite links for the published root. Keep the source files usable directly
# from web/site/web/server-browser without a second hand-edited HTML copy.
sed 's#../server-browser/#server-browser/#g' \
  "$STAGE/index.html" > "$STAGE/index.html.tmp"
mv "$STAGE/index.html.tmp" "$STAGE/index.html"

# Reject broken local references in the staged HTML before publishing. This
# intentionally stays shell-only so CI does not need another interpreter.
errors=0
for html in "$STAGE/index.html" "$STAGE/server-browser/index.html"; do
  html_dir=$(CDPATH= cd -- "$(dirname -- "$html")" && pwd)
  while IFS= read -r value; do
    [ -n "$value" ] || continue
    case "$value" in
      http://*|https://*|mailto:*|\#*) continue ;;
    esac
    value=${value%%#*}
    value=${value%%\?*}
    case "$value" in
      /*) target="$STAGE$value" ;;
      *) target=$(realpath -m "$html_dir/$value") ;;
    esac
    if [ ! -e "$target" ]; then
      echo "ERROR: staged Pages link is broken: ${html#$STAGE/} -> $value" >&2
      errors=1
    fi
  done < <(grep -hoE '(src|href)="[^"]+"' "$html" | cut -d '"' -f2)
done
[ "$errors" -eq 0 ] || exit 1

file_count=$(find "$STAGE" -type f | wc -l | tr -d ' ')
printf 'OK: staged %s Pages files with valid local links.\n' "$file_count"
if [ -n "$OUTPUT_DIR" ]; then
  printf 'Pages output: %s\n' "$STAGE"
else
  printf 'Pages validation complete; temporary tree removed.\n'
fi
