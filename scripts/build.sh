#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/out"
MAIN_CLASSES="$OUT_DIR/classes"
TEST_CLASSES="$OUT_DIR/test-classes"
JAR_FILE="$OUT_DIR/debugging-lab.jar"

rm -rf "$MAIN_CLASSES" "$TEST_CLASSES"
mkdir -p "$MAIN_CLASSES" "$TEST_CLASSES"

find "$ROOT_DIR/src/main/java" -name '*.java' -print0 | \
  xargs -0 javac --release 21 -encoding UTF-8 -d "$MAIN_CLASSES"

jar --create --file "$JAR_FILE" --main-class lab.LanguageAgnosticDebuggingLab -C "$MAIN_CLASSES" .

echo "Build completed: $JAR_FILE"
