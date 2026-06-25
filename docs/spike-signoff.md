# Spike Sign-Off Template

Release gate: spike validation on file before Play promotion (v0.x alpha onward).

## Metadata

| Field | Value |
|-------|-------|
| **Sign-off date** | YYYY-MM-DD |
| **Signer** | Name / role |
| **Device** | e.g. Pixel 8, API 36 |
| **DeciBoost build** | e.g. `0.1.0` (commit SHA) |
| **YouTube version** | e.g. `com.google.android.youtube` version code |

## Checklist (from `docs/spike-youtube-checklist.md`)

- [ ] 150% boost applied via `BoostTrampolineActivity`
- [ ] YouTube playback audible with boost for ≥30 s
- [ ] DeciBoost backgrounded (`pressHome`)
- [ ] Pause → resume in YouTube
- [ ] Boost restored within 1.5 s (subjective + `last_reapply=PLAYBACK_ACTIVE` in logcat)
- [ ] Probe dump shows `global_effect_enabled=true`, `target_gain_mb=1500`

## Probe evidence

```text
# Paste logcat line from:
adb logcat -d -s DeciBoostProbe | grep "delivered=true" | tail -1
```

## Result

- [ ] **PASS** — YouTube pause/resume hypothesis validated; proceed to release track.
- [ ] **FAIL** — Design review required (re-apply strategy).

## Notes

_Optional: OEM quirks, network conditions, or follow-up actions._

## Related gates

| Gate | When | Where |
|------|------|-------|
| Emulator harness | Every push to `main` | `.github/workflows/ci.yml` (`instrumented-api36`) |
| API matrix | Weekly + `release/*` | `.github/workflows/instrumented-matrix.yml` |
| Spike sign-off | Before Play promotion (manual) | This document |