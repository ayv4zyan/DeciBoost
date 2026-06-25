# DeciBoost — Android Volume Booster (100% → 200%)

**DeciBoost** is a modern Android **volume booster** that raises **media playback loudness above the system 100% cap** — without root. Built for **YouTube, Spotify, Netflix, browsers, and any app** that plays through Android’s standard audio mixer.

Unlike typical volume booster apps that lose boost after **pause → resume**, DeciBoost keeps working in the background with a **lifecycle-aware audio engine** tested on real devices (including **Honor** phones on Android 16).

[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-blue)](https://developer.android.com/about/versions/16)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## Why DeciBoost?

Android caps `STREAM_MUSIC` at 100%. DeciBoost uses **`LoudnessEnhancer`** and **`DynamicsProcessing`** on the global output mix (session 0) to increase perceived volume up to **200%**, with safety prompts at high levels.

| Problem users search for | How DeciBoost handles it |
|--------------------------|--------------------------|
| YouTube volume too quiet after pause/resume | Re-detects playback and **recreates** stale audio effects |
| Volume booster stops when app is backgrounded | **Foreground service** keeps the engine alive |
| Boost lost on Honor / MediaTek / Android 14+ | OEM-tested **release + recreate** on playback events |
| Need louder Spotify / Netflix / podcast audio | Works on **all media** through the output mix — not one app |

---

## Features

- **200% max boost** — slider from 100% to 200% with hearing-safety dialogs
- **Survives pause and resume** — YouTube, ReVanced, ExoPlayer/Media3 apps, music players
- **Background reliability** — foreground audio service + battery-opt-out onboarding
- **Playback-aware engine** — `AudioPlaybackCallback` + `isMusicActive` monitoring
- **Bluetooth & wired headphone support** — re-applies boost on route changes
- **Material 3 UI** — Jetpack Compose, edge-to-edge, adaptive layouts for tablets
- **Optional waveform visualizer** — opt-in `RECORD_AUDIO` for live output feedback
- **Safe boot restore** — notification to confirm boost after reboot (no silent auto-boost)
- **Tested in CI** — instrumented harness on emulators (API 26, 34, 36)

---

## Supported apps & scenarios

DeciBoost is **not limited to YouTube**. It boosts any audio routed through Android’s media output mix, including:

- **Video:** YouTube, YouTube ReVanced, NewPipe, Netflix, browsers (Chrome, Firefox)
- **Music:** Spotify, Apple Music, local players (VLC, Poweramp)
- **Podcasts & streaming:** Pocket Casts, Audible, web players
- **Games** with standard media audio output

> **Note:** DRM-protected or mixer-bypass paths may not be boostable. Phone calls and voice chat (`USAGE_VOICE_COMMUNICATION`) are intentionally excluded.

---

## How it works

1. You set boost (e.g. 150%) in the app.
2. A **foreground service** attaches post-processing effects to session 0.
3. A **playback monitor** watches for pause, resume, and config changes.
4. On resume (or OEM stale-effect detection), the engine **releases and recreates** `LoudnessEnhancer`, then re-applies gain within ~1.5 s.
5. A **watchdog** verifies the effect stays healthy while boost is active.

This fixes the common failure mode where boost *looks* enabled in software but disappears after the user pauses media — especially on **Honor, MediaTek, and Android 14+** devices.

---

## Screenshots

_App store screenshots are pending for v1.0.0-alpha._

---

## Requirements

| | |
|---|---|
| **Minimum Android** | 8.0 (API 26) |
| **Target / tested** | Android 16 (API 36) |
| **Root** | Not required |
| **Permissions** | Notifications (FGS), optional battery exemption, optional `RECORD_AUDIO` for visualizer |

---

## Download & install

### From source (debug)

```bash
git clone https://github.com/ayv4zyan/DeciBoost.git
cd DeciBoost
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug package: `com.deciboost.app.debug`

### First launch

1. Complete onboarding (notifications + battery optimization exemption recommended).
2. Set boost above 100%.
3. Play media in any app — boost applies globally to media output.

---

## Development

### Tech stack

- **Kotlin** + **Jetpack Compose** + **Material 3**
- **Hilt** dependency injection
- **DataStore** preferences
- Modular architecture: `:core:audio`, `:core:domain`, `:feature:boost`, `:testing:audio-harness`

### Build

```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK (minified)
./gradlew test                   # JVM unit tests
./gradlew detekt                 # Static analysis (all modules)
./gradlew :testing:audio-harness:connectedDebugAndroidTest  # Emulator harness
```

### Manual YouTube validation (physical device, optional)

Before a release, walk through [`docs/spike-youtube-checklist.md`](docs/spike-youtube-checklist.md) on a real phone with YouTube installed and record results in [`docs/spike-signoff.md`](docs/spike-signoff.md).

See also: [`DESIGN.md`](DESIGN.md).

---

## FAQ

### Does DeciBoost only work with YouTube?

**No.** YouTube is the primary regression test because pause/resume is a known pain point. Boost applies to **all media** on the output mix — Spotify, Netflix, games, browsers, etc.

### Why does boost disappear in other volume booster apps?

Many apps attach `LoudnessEnhancer(0)` once and never react when playback pauses or the `AudioTrack` is recreated. On some OEMs the effect handle goes stale. DeciBoost listens for lifecycle changes and recreates the effect.

### Does it work when DeciBoost is in the background?

**Yes.** A foreground service keeps the audio engine running while boost is active (persistent notification while engaged).

### Will this damage my speakers or hearing?

Boost above 100% can increase clipping and listening fatigue. DeciBoost shows safety warnings at elevated levels. Use headphones at moderate levels; prolonged max boost is not recommended.

### Do I need root or Magisk?

No. DeciBoost uses public Android audio effect APIs only.

### How is this different from BoostX?

DeciBoost adds playback lifecycle monitoring, a foreground service, effect recreate on resume, automated tests, and a modern Compose UI. BoostX is a minimal Activity-scoped booster; DeciBoost targets reliable real-world usage on OEM devices.

---

## Project structure

```
DeciBoost/
├── app/                    # Application shell, FGS, boot receiver
├── core/
│   ├── audio/              # Boost engine, playback monitor, effects
│   ├── domain/             # BoostController orchestration
│   └── data/               # DataStore preferences
├── feature/
│   ├── boost/              # Main boost UI
│   └── settings/           # Settings, onboarding, safety UX
├── testing/
│   ├── audio-harness/      # Instrumented pause/resume tests
│   └── scripts/            # Local emulator test helpers
└── docs/                   # Privacy, spike checklist, Play declarations
```

---

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/ayv4zyan/DeciBoost). See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

---

## Legal & policies

| Document | Description |
|----------|-------------|
| [LICENSE](LICENSE) | Apache License 2.0 |
| [docs/PRIVACY.md](docs/PRIVACY.md) | Privacy policy (local-only v1, no analytics) |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Community standards |

---

## License

Copyright 2026 [Artur Ayvazyan](https://github.com/ayv4zyan). Licensed under the [Apache License, Version 2.0](LICENSE).

---

## Keywords

Android volume booster · increase volume above 100% · loudness enhancer · media volume boost · YouTube volume booster · Spotify louder · pause resume volume fix · Honor volume booster · background volume booster · no root volume app · Android 16 volume · DynamicsProcessing · ExoPlayer volume boost

---

<p align="center">
  <strong>DeciBoost</strong> — louder media, even after pause and resume.
</p>