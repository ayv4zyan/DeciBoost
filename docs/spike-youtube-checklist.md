# Spike YouTube Checklist

Manual validation on a physical device with YouTube installed. Part of the **release process** before Play promotion — not a one-time PR gate.

## Prerequisites

- Debug APK installed: `com.deciboost.app.debug`
- `COMPONENT_PREFIX=com.deciboost.app.debug/com.deciboost.app.debug`

## Steps

1. Apply 150% boost via spike activity or trampoline:
   ```bash
   adb shell am start -n "${COMPONENT_PREFIX}.BoostTrampolineActivity" \
     -a com.deciboost.SET_BOOST --ei boost 150
   ```
2. Play YouTube video for 30 seconds.
3. Press Home (DeciBoost backgrounded).
4. Pause → resume in YouTube.
5. Confirm audible boost within 1.5 s (subjective + logcat `last_reapply=PLAYBACK_ACTIVE`).
6. Repeat on API 34 and API 36 devices if available.

## Probe dump

```bash
DEBUG_PKG="com.deciboost.app.debug"
COMPONENT_PREFIX="${DEBUG_PKG}/${DEBUG_PKG}"
adb logcat -c DeciBoostProbe:S 2>/dev/null || adb logcat -c
adb shell run-as "$DEBUG_PKG" sh -c \
  "am broadcast -a com.deciboost.DEBUG.DUMP_BOOST_STATE -n ${COMPONENT_PREFIX}.DebugBoostStateReceiver"
sleep 0.5
adb logcat -d -s DeciBoostProbe | grep "delivered=true" | tail -1
```

## Exit criteria

Pass on ≥1 physical device before Play promotion. Record results in `docs/spike-signoff.md`. Failure triggers design review of re-apply strategy.