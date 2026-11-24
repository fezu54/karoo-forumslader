#!/usr/bin/env bash
set -euo pipefail

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_FILE="$HOOK_DIR/GuardCommands.kt"
CACHE_DIR="$HOOK_DIR/.cache"
JAR_FILE="$CACHE_DIR/guard-commands.jar"

append_sdkman_path() {
    local sdkman_java="$HOME/.sdkman/candidates/java/current/bin"
    local sdkman_kotlin="$HOME/.sdkman/candidates/kotlin/current/bin"
    [ -d "$sdkman_java" ] && PATH="$sdkman_java:$PATH"
    [ -d "$sdkman_kotlin" ] && PATH="$sdkman_kotlin:$PATH"
    export PATH
}

command -v java >/dev/null 2>&1 && command -v kotlinc >/dev/null 2>&1 || append_sdkman_path

if ! command -v java >/dev/null 2>&1 || ! command -v kotlinc >/dev/null 2>&1; then
    echo '{"decision":"allow"}'
    exit 0
fi

if [ ! -f "$JAR_FILE" ] || [ "$SOURCE_FILE" -nt "$JAR_FILE" ]; then
    mkdir -p "$CACHE_DIR"
    TMP_JAR="$CACHE_DIR/guard-commands.$$.jar"
    if ! kotlinc "$SOURCE_FILE" -include-runtime -d "$TMP_JAR" >/dev/null 2>&1; then
        rm -f "$TMP_JAR"
        echo '{"decision":"allow"}'
        exit 0
    fi
    mv "$TMP_JAR" "$JAR_FILE"
fi

exec java -jar "$JAR_FILE"
