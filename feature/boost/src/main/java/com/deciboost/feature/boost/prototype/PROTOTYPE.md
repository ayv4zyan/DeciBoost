# PROTOTYPE — issue #9 waveform above the fold

**Status:** Promoted to product (**variant C waveform position only**, v0.2.1).

**Issue:** [#9 Keep waveform visible without scrolling on home screen](https://github.com/ayv4zyan/DeciBoost/issues/9)

## Question

How should **session status + a live audio indicator** stay visible on common phone heights **without scrolling**, while boost control remains usable (including large system fonts)?

## Verdict

**Winner (narrow): C — Gauge-integrated wave position only**

- Live waveform is drawn **inside** the boost arc (no separate bottom block).
- **Not** adopted from C: status chips, tighter spacing, smaller gauge, reordered hierarchy.
- Home chrome stays the previous layout (percent, sliders, original session status strip).

Implemented in `BoostScreen.kt` / `WaveformVisualizer.kt` (modifier-sized gauge inset).

## How to re-open the exploration

```bash
open feature/boost/src/main/java/com/deciboost/feature/boost/prototype/waveform-visibility-prototype.html
```

`waveform-visibility-prototype.html` remains a static side-by-side reference (A–E). Not production UI.

## Other variants (not chosen)

| Key | Name | Notes |
|-----|------|--------|
| A | Baseline stack | Documented the problem |
| B | Sticky live dock | Viable alternative |
| C full | Gauge + chips + compact stack | Only the **wave-in-gauge** piece was kept |
| D | Diagnostic-first | Not chosen |
| E | Inline meter path | Not chosen |
