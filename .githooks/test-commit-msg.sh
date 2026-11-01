#!/usr/bin/env bash
# Test suite for .githooks/commit-msg

set -e

HOOK_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/commit-msg"
TEMP_FILE=$(mktemp)

cleanup() {
    rm -f "$TEMP_FILE"
}
trap cleanup EXIT

passed=0
failed=0

run_test() {
    local expected_status="$1"
    local message="$2"
    local description="$3"

    printf "%s\n" "$message" > "$TEMP_FILE"

    local exit_code=0
    "$HOOK_PATH" "$TEMP_FILE" >/dev/null 2>&1 || exit_code=$?

    if [ "$expected_status" -eq 0 ] && [ "$exit_code" -eq 0 ]; then
        echo "PASS: $description"
        passed=$((passed + 1))
    elif [ "$expected_status" -ne 0 ] && [ "$exit_code" -ne 0 ]; then
        echo "PASS (rejected as expected): $description"
        passed=$((passed + 1))
    else
        echo "FAIL: $description (expected exit code $expected_status, got $exit_code)"
        failed=$((failed + 1))
    fi
}

echo "Running commit-msg hook tests..."
echo "--------------------------------"

# Positive tests (should pass with exit code 0)
run_test 0 "feat: add battery gauge widget" "Simple feat with lowercase subject"
run_test 0 "feat: Add battery gauge widget" "Simple feat with uppercase initial subject"
run_test 0 "fix(bluetooth): resolve reconnect timeout" "Scoped fix with lowercase subject"
run_test 0 "fix(bluetooth): Resolve reconnect timeout" "Scoped fix with uppercase initial subject"
run_test 0 "refactor: simplify state machine" "Refactor commit"
run_test 0 "refactor!: drop legacy parser" "Breaking change with !"
run_test 0 "feat(ui)!: rewrite layout engine" "Scoped breaking change with !"
run_test 0 "[BREAKING] feat: change core protocol" "Legacy [BREAKING] prefix"
run_test 0 "docs(readme): add configuration instructions" "Docs commit"
run_test 0 "style: format code according to kotlin style" "Style commit"
run_test 0 "perf: optimize packet parsing" "Perf commit"
run_test 0 "test(domain): add unit tests for voltage calculations" "Test commit"
run_test 0 "build(deps): bump kotest from 6.2.5 to 6.2.6" "Build commit with scoped deps"
run_test 0 "ci: update github actions workflow" "CI commit"
run_test 0 "chore: update coverage baseline [skip ci]" "Chore commit with skip ci"
run_test 0 "revert: revert previous faulty commit" "Revert commit"

# Android Studio simulation: comment lines and empty lines before/after subject
run_test 0 "# Android Studio generated comments
# Please enter the commit message for your changes.

feat(ui): implement new cards layout

# More comments here
# ------------------------ >8 ------------------------" "Android Studio template with comments and whitespace"

# Merge commits (should pass)
run_test 0 "Merge branch 'feature-branch' into main" "Standard merge commit"
run_test 0 "Merge remote-tracking branch 'origin/main'" "Remote-tracking merge commit"

# Negative tests (should fail with exit code 1)
run_test 1 "Fix: bad case in type" "Uppercase type Fix:"
run_test 1 "FEAT: all caps type" "All caps type FEAT:"
run_test 1 "Refactor: bad case" "Uppercase type Refactor:"
run_test 1 "wip: working on changes" "Disallowed type wip"
run_test 1 "WIP" "Disallowed commit message WIP"
run_test 1 "Updated readme" "Free-form commit message without prefix"
run_test 1 "fix bug in voltage calculation" "Commit message without type colon"
run_test 1 "feat:" "Type with colon but no description"
run_test 1 "feat:   " "Type with colon and whitespace only description"
run_test 1 "unknown(core): something" "Unknown type unknown"

echo "--------------------------------"
echo "Results: $passed passed, $failed failed."

if [ "$failed" -gt 0 ]; then
    exit 1
fi
