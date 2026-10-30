#!/usr/bin/env bash
# Keep the single "Vendored jar review" issue in step with the generated report.
#
# RuneWake vendors its Java libraries, so Dependabot has nothing to update for
# them (see .github/dependabot.yml). The scheduled workflow runs
# scripts/check_vendored_dependencies.py, and this script turns its report into
# exactly one open issue:
#
#   status=review  -> create the issue, or rewrite the body of the existing one
#   status=current -> comment on and close the existing issue, create nothing
#
# Editing rather than recreating matters: the issue number stays stable, so
# links and discussion survive a weekly refresh, and a run with nothing to
# report does not reopen an issue somebody already read.
#
# Usage: bash scripts/open_dependency_review_issue.sh [report.md]
# Requires the gh CLI and GH_TOKEN (or GITHUB_TOKEN).
#
# Local dry run: DRY_RUN=1 bash scripts/open_dependency_review_issue.sh report.md

set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

REPORT="${1:-vendored-deps-report.md}"
STATUS_FILE="${REPORT}.status"
TITLE="Vendored jar review"
LABEL="dependencies"

dry_run="${DRY_RUN:-0}"

die() {
	printf 'ERROR: %s\n' "$*" >&2
	exit 1
}

[ -f "$REPORT" ] || die "report not found: $REPORT (run scripts/check_vendored_dependencies.py --output $REPORT)"
[ -f "$STATUS_FILE" ] || die "status file not found: $STATUS_FILE"

status=$(tr -d '[:space:]' <"$STATUS_FILE")
case "$status" in
current | review) ;;
*) die "unexpected status '$status' in $STATUS_FILE" ;;
esac

command -v gh >/dev/null 2>&1 || die "the gh CLI is required"
if [ -z "${GH_TOKEN:-}${GITHUB_TOKEN:-}" ] && [ "$dry_run" != "1" ]; then
	die "GH_TOKEN or GITHUB_TOKEN must be set"
fi

# In Actions the repository is explicit; locally gh resolves it from the remote.
repo_args=()
if [ -n "${GITHUB_REPOSITORY:-}" ]; then
	repo_args=(--repo "$GITHUB_REPOSITORY")
fi
gh_api() { gh ${repo_args[@]+"${repo_args[@]}"} "$@"; }

if [ "$dry_run" = "1" ]; then
	printf 'DRY RUN: status=%s report=%s (%s lines)\n' "$status" "$REPORT" "$(wc -l <"$REPORT")"
	printf 'DRY RUN: title=%s label=%s\n' "$TITLE" "$LABEL"
	exit 0
fi

# A missing label is not a failure; it is created once and then reused.
gh_api label create "$LABEL" --color 0366d6 \
	--description "Dependency and vendored-jar maintenance" >/dev/null 2>&1 || true

existing=$(
	gh_api issue list --state open --limit 100 --json number,title \
		--jq ".[] | select(.title == \"$TITLE\") | .number" 2>/dev/null | head -n 1
)

if [ "$status" = "current" ]; then
	if [ -z "$existing" ]; then
		echo "OK: every vendored jar is current; no issue to maintain"
		exit 0
	fi
	gh_api issue comment "$existing" --body "Every vendored jar now matches its upstream release line, and the manifest covers every jar on disk. Closing; the weekly check will reopen this if something moves." >/dev/null
	gh_api issue close "$existing" --reason completed >/dev/null
	echo "OK: closed issue #$existing (all vendored jars current)"
	exit 0
fi

if [ -n "$existing" ]; then
	gh_api issue edit "$existing" --body-file "$REPORT" >/dev/null
	echo "OK: updated issue #$existing"
else
	gh_api issue create --title "$TITLE" --body-file "$REPORT" --label "$LABEL"
fi
