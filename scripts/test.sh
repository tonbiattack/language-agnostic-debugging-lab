#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/out"
MAIN_CLASSES="$OUT_DIR/classes"
TEST_CLASSES="$OUT_DIR/test-classes"

"$ROOT_DIR/scripts/build.sh"

find "$ROOT_DIR/src/test/java" -name '*.java' -print0 | \
  xargs -0 javac --release 21 -encoding UTF-8 -cp "$MAIN_CLASSES" -d "$TEST_CLASSES"

java -cp "$MAIN_CLASSES:$TEST_CLASSES" lab.LabEngineTest
