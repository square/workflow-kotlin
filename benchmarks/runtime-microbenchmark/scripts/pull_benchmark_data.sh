#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-}"
OUT_DIR="${2:-.amp/in/artifacts/benchmark-runs/benchmark-data}"
ON_DEVICE_PATH="/sdcard/Android/media/com.squareup.benchmark.runtime.benchmark.test/additional_test_output/com.squareup.benchmark.runtime.benchmark.test-benchmarkData.json"
LOCAL_SEARCH_ROOT="benchmarks/runtime-microbenchmark/build/outputs/connected_android_test_additional_output"

mkdir -p "$OUT_DIR"

if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
else
  ADB=(adb)
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
DEST="$OUT_DIR/benchmarkData-$STAMP.json"

if "${ADB[@]}" pull "$ON_DEVICE_PATH" "$DEST" >/dev/null 2>&1; then
  echo "$DEST"
  exit 0
fi

# Fallback: benchmark runner may already have moved the file into Gradle outputs.
LATEST_LOCAL="$(find "$LOCAL_SEARCH_ROOT" -type f -name '*benchmarkData.json' 2>/dev/null | sort | tail -n 1)"
if [[ -n "$LATEST_LOCAL" && -f "$LATEST_LOCAL" ]]; then
  cp "$LATEST_LOCAL" "$DEST"
  echo "$DEST"
  exit 0
fi

echo "Failed to locate benchmarkData.json on device or in $LOCAL_SEARCH_ROOT" >&2
exit 1
