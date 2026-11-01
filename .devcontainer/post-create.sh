#!/usr/bin/env bash
set -e

# Configure git hooks directory
git config core.hooksPath .githooks
chmod +x .githooks/* 2>/dev/null || true

echo "Git hooks configured successfully in Dev Container."
