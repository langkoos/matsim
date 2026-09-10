#!/usr/bin/env bash

set -uo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
MODULE_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(git -C "$MODULE_DIR" rev-parse --show-toplevel 2>/dev/null) || {
	printf 'MC quality: cannot locate the Git repository.\n' >&2
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
MVN=(mvn --batch-mode -f "$MODULE_DIR/pom.xml" -Pmc-quality -Dmatsim.preferLocalDtds=true -Dmaven.test.redirectTestOutputToFile=true -Dsource.skip)
SPOTLESS_FILES=

print_help() {
	cat <<'EOF'
Usage: quality.sh [--pre-commit]

Runs MC formatting, lint, static-analysis, unit-test, and coverage gates.
The pre-commit mode skips commits unrelated to MC and rejects partially
staged MC files so the working tree cannot be mistaken for the staged commit.
EOF
}

is_relevant_path() {
	case "$1" in
		contribs/mobilityconsumption/src/*.java | contribs/mobilityconsumption/src/**/*.java | \
		contribs/mobilityconsumption/pom.xml | contribs/mobilityconsumption/quality/* | \
		contribs/mobilityconsumption/scripts/* | contribs/mobilityconsumption/QUALITY.md)
			return 0
			;;
		*) return 1 ;;
	esac
}

check_staged_files() {
	local path
	local staged_file
	local -a staged=()
	local -a relevant=()

	staged_file=$(mktemp "${TMPDIR:-/tmp}/mc-staged.XXXXXX") || {
		printf 'MC quality: cannot create a temporary staged-file list.\n' >&2
		exit 2
	}
	if ! git -C "$REPO_ROOT" diff --cached --name-only --diff-filter=ACMRD -z >"$staged_file"; then
		rm -f -- "$staged_file"
		printf 'MC quality: cannot inspect staged files.\n' >&2
		exit 2
	fi
	while IFS= read -r -d '' path; do
		staged+=("$path")
	done <"$staged_file"
	rm -f -- "$staged_file"
	for path in ${staged[@]+"${staged[@]}"}; do
		if is_relevant_path "$path"; then
			relevant+=("$path")
		fi
	done

	if ((${#relevant[@]} == 0)); then
		printf 'MC quality: no relevant staged files; skipping.\n'
		exit 0
	fi

	for path in "${relevant[@]}"; do
		if ! git -C "$REPO_ROOT" diff --quiet -- "$path"; then
			printf 'MC quality: partially staged file detected: %s\n' "$path" >&2
			printf 'Stage or stash its remaining changes, then retry the commit.\n' >&2
			exit 1
		fi
	done
}

run_stage() {
	local name=$1
	local remedy=$2
	shift 2

	printf '\n==> %s\n' "$name"
	if "$@"; then
		printf 'PASS: %s\n' "$name"
	else
		printf '\nFAIL: %s\n%s\n' "$name" "$remedy" >&2
		exit 1
	fi
}

find_changed_java() {
	local path
	local changed_file
	local -a changed=()

	changed_file=$(mktemp "${TMPDIR:-/tmp}/mc-java.XXXXXX") || {
		printf 'MC quality: cannot create a temporary changed-file list.\n' >&2
		exit 2
	}
	if ! git -C "$REPO_ROOT" diff --name-only --diff-filter=ACMR -z "$BASE_REF" -- \
		contribs/mobilityconsumption/src/main/java \
		contribs/mobilityconsumption/src/test/java >"$changed_file"; then
		rm -f -- "$changed_file"
		printf 'MC quality: cannot determine Java changes from %s.\n' "$BASE_REF" >&2
		exit 2
	fi
	while IFS= read -r -d '' path; do
		changed+=("$path")
	done <"$changed_file"
	rm -f -- "$changed_file"

	for path in ${changed[@]+"${changed[@]}"}; do
		path=${path#contribs/mobilityconsumption/}
		if [[ -z "$SPOTLESS_FILES" ]]; then
			SPOTLESS_FILES=$path
		else
			SPOTLESS_FILES+=",$path"
		fi
	done
}

clear_stale_test_reports() {
	local report
	for report in "$MODULE_DIR"/target/surefire-reports/TEST-*.xml; do
		[[ -e "$report" ]] || continue
		rm -- "$report" || {
			printf 'MC quality: cannot remove stale test report: %s\n' "$report" >&2
			return 1
		}
	done
}

case "${1:-}" in
	--pre-commit) check_staged_files ;;
	-h | --help) print_help; exit 0 ;;
	"") ;;
	*) print_help >&2; exit 2 ;;
esac

command -v mvn >/dev/null 2>&1 || {
	printf 'MC quality: Maven is required but was not found on PATH.\n' >&2
	exit 2
}
command -v java >/dev/null 2>&1 || {
	printf 'MC quality: Java 25 is required but was not found on PATH.\n' >&2
	exit 2
}

find_changed_java

run_stage \
	'Dependency preparation' \
	'Fix the Maven compilation/dependency error shown above.' \
	mvn --batch-mode -pl contribs/mobilityconsumption -am -DskipTests -Dsource.skip install -f "$REPO_ROOT/pom.xml"

if [[ -n "$SPOTLESS_FILES" ]]; then
	run_stage \
		'Formatting' \
		"Run $SCRIPT_DIR/format.sh, review the changes, and stage them." \
		"${MVN[@]}" "-DspotlessFiles=$SPOTLESS_FILES" spotless:check
else
	printf '\n==> Formatting\nPASS: Formatting (no Java changes from %s)\n' "$BASE_REF"
fi

run_stage \
	'Lint' \
	"Resolve the Checkstyle violations listed above; rules are in $MODULE_DIR/quality/checkstyle.xml." \
	"${MVN[@]}" checkstyle:check

run_stage \
	'Static analysis' \
	"Resolve the high-priority SpotBugs findings above; XML details are in $MODULE_DIR/target/spotbugsXml.xml." \
	"${MVN[@]}" compile spotbugs:check

clear_stale_test_reports || exit 2
run_stage \
	'Unit tests and coverage' \
	"Fix the failing test or add coverage. Reports are under $MODULE_DIR/target/surefire-reports and target/site/jacoco." \
	"${MVN[@]}" verify

run_stage \
	'Quality metrics ratchet' \
	"Restore every regressed metric shown above. After an improvement, run $SCRIPT_DIR/quality-metrics.sh --ratchet and commit the raised baseline." \
	"$SCRIPT_DIR/quality-metrics.sh" --check

printf '\nMC quality gate passed.\n'
