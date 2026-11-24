#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_FILE="$SCRIPT_DIR/SetupWorktree.kt"
CACHE_DIR="$SCRIPT_DIR/.cache"
JAR_FILE="$CACHE_DIR/setup-worktree.jar"

append_sdkman_path() {
    local sdkman_java="$HOME/.sdkman/candidates/java/current/bin"
    local sdkman_kotlin="$HOME/.sdkman/candidates/kotlin/current/bin"
    [ -d "$sdkman_java" ] && PATH="$sdkman_java:$PATH"
    [ -d "$sdkman_kotlin" ] && PATH="$sdkman_kotlin:$PATH"
    export PATH
}

command -v java >/dev/null 2>&1 && command -v kotlinc >/dev/null 2>&1 || append_sdkman_path

if [ ! -f "$JAR_FILE" ] || [ "$SOURCE_FILE" -nt "$JAR_FILE" ]; then
    mkdir -p "$CACHE_DIR"
    TMP_JAR="$CACHE_DIR/setup-worktree.$$.jar"
    kotlinc "$SOURCE_FILE" -include-runtime -d "$TMP_JAR"
    mv "$TMP_JAR" "$JAR_FILE"
fi

exec java -jar "$JAR_FILE" "$@"
