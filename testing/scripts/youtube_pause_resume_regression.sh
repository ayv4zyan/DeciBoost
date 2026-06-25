#!/usr/bin/env bash
set -euo pipefail
DEBUG_PKG="com.deciboost.app.debug"
COMPONENT_PREFIX="${DEBUG_PKG}/${DEBUG_PKG}"
YOUTUBE="com.google.android.youtube"
VIDEO_URL="${VIDEO_URL:-https://www.youtube.com/shorts/jNQXAC9IVRw}"
MAX_RETRIES=3
GA_STRICT="${GA_STRICT:-true}"

dump_probe() {
  local outfile="$1"
  adb logcat -c DeciBoostProbe:S 2>/dev/null || adb logcat -c
  adb shell run-as "$DEBUG_PKG" sh -c \
    "am broadcast -a com.deciboost.DEBUG.DUMP_BOOST_STATE -n ${COMPONENT_PREFIX}.DebugBoostStateReceiver"
  sleep 0.5
  adb logcat -d -s DeciBoostProbe | grep "delivered=true" | tail -1 | tee "$outfile"
  grep -q "delivered=true" "$outfile"
}

parse_probe() {
  local file="$1" prefix="$2"
  eval "${prefix}_GAIN=\$(grep -o 'target_gain_mb=[0-9]*' \"$file\" | cut -d= -f2)"
  eval "${prefix}_ENABLED=\$(grep -o 'global_effect_enabled=[a-z]*' \"$file\" | cut -d= -f2)"
  eval "${prefix}_RMS=\$(grep -o 'rms_ratio=[0-9.]*' \"$file\" | cut -d= -f2)"
}

for attempt in $(seq 1 $MAX_RETRIES); do
  adb shell am force-stop "$DEBUG_PKG"
  adb install -r app/build/outputs/apk/debug/app-debug.apk

  adb shell pm grant "$DEBUG_PKG" android.permission.RECORD_AUDIO

  adb shell am start -n "${COMPONENT_PREFIX}.BoostTrampolineActivity" \
    -a com.deciboost.SET_BOOST --ei boost 150
  sleep 2

  adb shell am start -a android.intent.action.VIEW -d "$VIDEO_URL" "$YOUTUBE"
  for i in $(seq 1 30); do
    adb shell dumpsys audio | grep -q "player state: started" && break
    sleep 1
  done

  FOCUS=$(adb shell dumpsys audio | grep -i "focus" | head -5)
  echo "focus: $FOCUS"

  dump_probe /tmp/pre.txt || { echo "pre dump failed (receiver not delivered)"; continue; }
  parse_probe /tmp/pre.txt PRE

  adb shell input keyevent KEYCODE_MEDIA_PAUSE
  sleep 2
  adb shell input keyevent KEYCODE_MEDIA_PLAY
  sleep 3

  dump_probe /tmp/post.txt || { echo "post dump failed (receiver not delivered)"; continue; }
  parse_probe /tmp/post.txt POST

  adb shell dumpsys media.audio_flinger | grep -i "LoudnessEnhancer" | tee /tmp/flinger.txt

  PROBE_OK=false
  if [[ "$PRE_GAIN" == "$POST_GAIN" && "$POST_ENABLED" == "true" ]]; then
    PROBE_OK=true
  fi

  RMS_OK=true
  if [[ "$GA_STRICT" == "true" ]]; then
    if [[ -n "$PRE_RMS" && -n "$POST_RMS" ]] && \
       awk "BEGIN {exit !($PRE_RMS >= 0.85 && $POST_RMS >= 0.85)}"; then
      RMS_OK=true
    else
      RMS_OK=false
    fi
  fi

  if [[ "$PROBE_OK" == "true" && "$RMS_OK" == "true" ]]; then
    echo "PASS attempt $attempt (gain=$PRE_GAIN rms_pre=$PRE_RMS rms_post=$POST_RMS ga_strict=$GA_STRICT)"
    exit 0
  fi
  echo "Retry $attempt failed (probe=$PROBE_OK rms=$RMS_OK pre=$PRE_GAIN post=$POST_GAIN rms_post=$POST_RMS)"
  sleep 5
done
echo "FAIL after $MAX_RETRIES attempts"; exit 1