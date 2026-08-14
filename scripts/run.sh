#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_FILE="$ROOT_DIR/out/debugging-lab.jar"

if [[ ! -f "$JAR_FILE" ]]; then
  "$ROOT_DIR/scripts/build.sh"
fi

exec java -jar "$JAR_FILE" "$@"
