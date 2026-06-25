# Self-Hosted Pixel Runner Setup

Dedicated hardware runner for nightly YouTube regression (`GA_STRICT=true`).

## Hardware

- **Device:** Google Pixel (API 34 or 36 recommended)
- **Connection:** USB to CI host, `adb devices` shows serial
- **Screen:** Stay awake (`adb shell settings put system screen_off_timeout 2147483647`)

## GitHub Actions runner

1. Install a self-hosted runner on the CI host (Linux or macOS).
2. Label the runner: `deciboost-youtube`
3. Ensure the runner user is in the `plugdev` group (Linux) for USB access.

```bash
# Verify device visibility from runner account
adb devices -l
```

## Device preparation

1. Enable **Developer options** and **USB debugging**.
2. Log in to **YouTube** (`com.google.android.youtube`) with a test account.
3. Disable animations for stability:

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

4. Install debug APK before first nightly run (workflow also installs):

```bash
DEBUG_PKG="com.deciboost.app.debug"
COMPONENT_PREFIX="${DEBUG_PKG}/${DEBUG_PKG}"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant "$DEBUG_PKG" android.permission.RECORD_AUDIO
```

## Nightly invocation

Workflow: `.github/workflows/nightly-youtube.yml`

```bash
GA_STRICT=true ./testing/scripts/youtube_pause_resume_regression.sh
```

`GA_STRICT=true` requires (physical Pixel — **strict** RMS gate):

- `global_effect_enabled=true` pre/post pause-resume
- `target_gain_mb` unchanged
- `rms_ratio >= 0.85` pre/post

> **Contrast with emulator CI:** merge-blocking jobs on AVDs (`ci.yml`, `instrumented-matrix.yml` API 36) use signal-present RMS (`>= 0.05`) plus engine state only. Absolute `0.85` is reserved for physical devices and this nightly runner. See `DESIGN.md` Layer 2 — "Emulator vs physical device (RMS policy)".

## COMPONENT_PREFIX reference

| Constant | Value |
|----------|-------|
| `DEBUG_PKG` | `com.deciboost.app.debug` |
| `COMPONENT_PREFIX` | `com.deciboost.app.debug/com.deciboost.app.debug` |

Always use full `${COMPONENT_PREFIX}.BoostTrampolineActivity` — never shorthand `.debug.Foo`.

## Maintenance

- Re-verify YouTube login monthly (session expiry).
- Keep USB cable seated; flaky `adb` disconnects fail the nightly gate.
- After OS updates, re-grant `RECORD_AUDIO` and confirm `adb devices` stable.