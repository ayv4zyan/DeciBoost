#!/usr/bin/env bash
# Boots an API 36 emulator (creating AVD + SDK deps if needed) and runs instrumented tests.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

AVD_NAME="${AVD_NAME:-DeciBoost_API36}"
API_LEVEL="${API_LEVEL:-36}"
SYSTEM_IMAGE="system-images;android-${API_LEVEL};google_apis;arm64-v8a"
EMULATOR_PID=""
EMULATOR_LOG="/tmp/deciboost-emulator.log"

cleanup() {
  if [[ -n "${EMULATOR_PID}" ]] && kill -0 "${EMULATOR_PID}" 2>/dev/null; then
    echo "Stopping emulator (pid ${EMULATOR_PID})..."
    adb -s emulator-5554 emu kill 2>/dev/null || kill "${EMULATOR_PID}" 2>/dev/null || true
    wait "${EMULATOR_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

ensure_cmdline_tools() {
  if [[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    return
  fi
  echo "Installing Android cmdline-tools..."
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  local tmpzip="/tmp/cmdline-tools.zip"
  curl -fsSL -o "$tmpzip" "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
  rm -rf /tmp/cmdline-tools-unpack
  unzip -q "$tmpzip" -d /tmp/cmdline-tools-unpack
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv /tmp/cmdline-tools-unpack/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  rm -f "$tmpzip"
}

ensure_sdk_packages() {
  ensure_cmdline_tools
  yes | sdkmanager --licenses >/dev/null
  sdkmanager \
    "platform-tools" \
    "emulator" \
    "platforms;android-${API_LEVEL}" \
    "${SYSTEM_IMAGE}"
}

ensure_avd() {
  if emulator -list-avds | grep -qx "${AVD_NAME}"; then
    return
  fi
  echo "Creating AVD ${AVD_NAME}..."
  echo "no" | avdmanager create avd -n "${AVD_NAME}" -k "${SYSTEM_IMAGE}" -d pixel_6 --force
}

wait_for_boot() {
  local serial="$1"
  adb -s "$serial" wait-for-device
  echo "Waiting for emulator boot..."
  for _ in $(seq 1 120); do
    local boot
    boot="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [[ "$boot" == "1" ]]; then
      adb -s "$serial" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
      adb -s "$serial" shell wm dismiss-keyguard >/dev/null 2>&1 || true
      return 0
    fi
    sleep 2
  done
  echo "Emulator failed to boot within 4 minutes" >&2
  tail -50 "$EMULATOR_LOG" >&2 || true
  return 1
}

start_emulator_if_needed() {
  local serial="emulator-5554"
  if adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -q .; then
    serial="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
    echo "Using already-running device: ${serial}"
    export ANDROID_SERIAL="$serial"
    return
  fi

  ensure_sdk_packages
  ensure_avd

  echo "Starting emulator ${AVD_NAME}..."
  nohup emulator -avd "${AVD_NAME}" \
    -no-snapshot-save \
    -gpu swiftshader_indirect \
    -no-boot-anim \
    >"$EMULATOR_LOG" 2>&1 &
  EMULATOR_PID=$!
  wait_for_boot "$serial"
  export ANDROID_SERIAL="$serial"
}

resolve_java_home() {
  local portable_jdk="$HOME/.jdks/temurin-21/Contents/Home"
  if [[ -x "$portable_jdk/bin/java" ]]; then
    export JAVA_HOME="$portable_jdk"
    return
  fi
  if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
    return
  fi
  echo "JDK 21 required. Install Temurin 21 or run:" >&2
  echo "  mkdir -p ~/.jdks/temurin-21 && curl -fsSL <temurin21.tar.gz> | tar -xz -C ~/.jdks/temurin-21 --strip-components=1" >&2
  exit 1
}

run_tests() {
  resolve_java_home
  chmod +x ./gradlew

  echo "Building debug APKs..."
  ./gradlew :app:assembleDebug :testing:audio-harness:assembleDebug --no-daemon

  local app_apk="app/build/outputs/apk/debug/app-debug.apk"
  if [[ ! -f "$app_apk" ]]; then
    echo "Missing app APK at $app_apk" >&2
    exit 1
  fi

  echo "Installing target app ($app_apk)..."
  adb install -r -d "$app_apk"

  echo "Running audio harness instrumented tests..."
  ./gradlew :testing:audio-harness:connectedDebugAndroidTest --no-daemon

  echo "Running app instrumented tests..."
  ./gradlew :app:connectedDebugAndroidTest --no-daemon
}

start_emulator_if_needed
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true

run_tests
echo "All emulator instrumented tests passed."