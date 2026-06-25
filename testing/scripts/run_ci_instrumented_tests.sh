#!/usr/bin/env bash
# Runs instrumented tests on an already-booted CI emulator.
# Set API_LEVEL=36 to include :app:connectedDebugAndroidTest (merge gate).
set -euo pipefail

API_LEVEL="${API_LEVEL:-36}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

chmod +x ./gradlew

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

./gradlew :app:assembleDebug :testing:audio-harness:assembleDebug --no-daemon

harness_ok=false
app_ok=false
harness_rerun_done=false
app_rerun_done=false
attempt=0
max_attempts=3
run_app_tests=false
if [ "$API_LEVEL" = "36" ]; then
  run_app_tests=true
fi

while [ "$attempt" -lt "$max_attempts" ]; do
  attempt=$((attempt + 1))
  if [ "$harness_ok" = false ]; then
    harness_rerun_flag=""
    if [ "$harness_rerun_done" = false ]; then
      harness_rerun_flag="--rerun-tasks"
      harness_rerun_done=true
    fi
    if ./gradlew :testing:audio-harness:connectedDebugAndroidTest --no-daemon $harness_rerun_flag; then
      harness_ok=true
    fi
  fi
  if [ "$run_app_tests" = true ] && [ "$harness_ok" = true ] && [ "$app_ok" = false ]; then
    app_rerun_flag=""
    if [ "$app_rerun_done" = false ]; then
      app_rerun_flag="--rerun-tasks"
      app_rerun_done=true
    fi
    if ./gradlew :app:connectedDebugAndroidTest --no-daemon $app_rerun_flag; then
      app_ok=true
    fi
  fi
  if [ "$run_app_tests" = false ] && [ "$harness_ok" = true ]; then
    exit 0
  fi
  if [ "$harness_ok" = true ] && [ "$app_ok" = true ]; then
    exit 0
  fi
  echo "Instrumented tests failed (attempt $attempt/$max_attempts), retrying in 15s..."
  sleep 15
done

exit 1