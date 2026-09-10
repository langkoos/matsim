#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
MODULE_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(git -C "$MODULE_DIR" rev-parse --show-toplevel 2>/dev/null) || {
	printf 'MC formatter: cannot locate the Git repository.\n' >&2
	exit 2
}

# The branch that "changed files" are measured against. Override with QUALITY_BASE_REF. Falls back to
# upstream/main when the fork has no origin/main, as is the case for forks whose default branch is master.
resolve_base_ref() {
	local candidate
	for candidate in "${QUALITY_BASE_REF:-}" origin/main upstream/main; do
		[[ -n "$candidate" ]] || continue
		if git -C "$REPO_ROOT" rev-parse --verify --quiet "$candidate^{commit}" >/dev/null; then
			printf '%s\n' "$candidate"
			return 0
		fi
	done
	return 1
}
BASE_REF=$(resolve_base_ref) || {
	printf 'MC quality: neither origin/main nor upstream/main exists; set QUALITY_BASE_REF.\n' >&2
	exit 2
}
CHANGED_FILE=$(mktemp "${TMPDIR:-/tmp}/mc-java.XXXXXX") || {
	printf 'MC formatter: cannot create a temporary changed-file list.\n' >&2
	exit 2
}
trap 'rm -f -- "$CHANGED_FILE"' EXIT

command -v mvn >/dev/null 2>&1 || {
	printf 'MC formatter: Maven is required but was not found on PATH.\n' >&2
	exit 2
}

git -C "$REPO_ROOT" diff --name-only --diff-filter=ACMR -z "$BASE_REF" -- \
	contribs/mobilityconsumption/src/main/java \
	contribs/mobilityconsumption/src/test/java >"$CHANGED_FILE"

SPOTLESS_FILES=
while IFS= read -r -d '' path; do
	path=${path#contribs/mobilityconsumption/}
	if [[ -z "$SPOTLESS_FILES" ]]; then
		SPOTLESS_FILES=$path
	else
		SPOTLESS_FILES+=",$path"
	fi
done <"$CHANGED_FILE"

if [[ -z "$SPOTLESS_FILES" ]]; then
	printf 'No MC Java changes from %s to format.\n' "$BASE_REF"
	exit 0
fi

mvn --batch-mode \
	-f "$MODULE_DIR/pom.xml" \
	-Pmc-quality \
	"-DspotlessFiles=$SPOTLESS_FILES" \
	-Dmatsim.preferLocalDtds=true \
	-Dsource.skip \
	spotless:apply

printf '\nFormatting applied. Review and stage the resulting changes.\n'
