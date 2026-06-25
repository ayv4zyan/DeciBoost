# Play Console — Special Use FGS Declaration (Draft)

**Subtype:** `audio_volume_boost`  
**FGS type:** `specialUse`  
**Property:** `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE = audio_volume_boost`

## User-facing feature description

DeciBoost is a media volume booster that applies post-processing audio effects (`LoudnessEnhancer` / `DynamicsProcessing` on session 0) so users can raise perceived loudness above the system 100% cap. The foreground service keeps the audio engine alive while boost is active, re-applies effects when playback sessions change (e.g. YouTube pause → resume), and shows a persistent notification while boost is engaged.

## Why a foreground service is required

1. **Lifecycle survival** — Boost must outlive the Activity; competitors that scope effects to an Activity lose boost when backgrounded or on pause/resume churn.
2. **Continuous monitoring** — `AudioPlaybackCallback` and output-device listeners must run while boost > 100% to re-apply effects deterministically.
3. **User visibility** — Android requires a visible notification for ongoing audio processing that affects global output mix.

## Why `specialUse` (not `mediaPlayback`)

DeciBoost does not play media; it enhances audio produced by other apps. The service performs real-time audio effect management on the global output mix, which does not fit `mediaPlayback` or `microphone` subtypes.

## User control & data minimization

- Boost is **opt-in** after onboarding; default is 100% (no enhancement).
- User can disable boost instantly via **Kill switch** (resets to 100%, stops FGS).
- **Auto-start on boot** only posts a restore notification; boost is never applied silently after reboot.
- No audio content is recorded, stored, or transmitted. Optional waveform visualizer is off by default and requires separate `RECORD_AUDIO` consent.

## Compliance notes (for legal review)

- Prominent hearing/speaker safety warnings in-app at elevated boost levels.
- Global boost may amplify notifications and accessibility audio; disclosed in Settings.
- Service stops when boost returns to 100% and idle timeout elapses.

---

*Draft for Play Console “Foreground service permissions” form — refine wording with legal before submission.*