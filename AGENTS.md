# AGENTS.md — DeciBoost

Instructions for AI agents and contributors working in this repository.

## Versioning

DeciBoost uses [Semantic Versioning 2.0.0](https://semver.org/) (`MAJOR.MINOR.PATCH`).

### Channel meaning

| `versionName` | Channel | Meaning |
|---------------|---------|---------|
| `0.x.y` | **Alpha** | Pre-release. Breaking changes allowed. Not marketed as stable. |
| `1.0.0` | **First stable release** | Public GA / Play Store production track. |
| `1.x.y` | **Stable** | Post-GA releases (see bump rules below). |

Do **not** use suffixes like `-alpha` or `-beta` in `versionName`. The major version encodes the channel: `0` = alpha, `1+` = release.

### Source of truth

Edit **only** `app/build.gradle.kts`:

```kotlin
versionCode = <int>   // monotonic integer for Play Store; bump on every store upload
versionName = "x.y.z"
```

`AboutScreen` reads `versionName` from the package manager — no duplicate string elsewhere.

### Doc sync on version bump (required)

Whenever you change `versionName` or `versionCode` in `app/build.gradle.kts`, **sync stale version references in the same commit** — do not leave docs behind.

**Always update** (current-state references → new `versionName`):

| File | What to sync |
|------|----------------|
| `AGENTS.md` | `### Current version` block |
| `DESIGN.md` | Status table row (`v{x.y.z} alpha`) and any “implemented / current as of v…” lines |
| `docs/PRIVACY.md` | `**Version:**` header; “In v{x.y.z} (alpha)” app-policy note |
| `docs/spike-signoff.md` | Example `DeciBoost build` field |
| `docs/16kb-page-size.md` | “v{x.y.z} has no native libs” (or equivalent current-build note) |
| `README.md` | Version-pinned **current** status (e.g. screenshots pending line) |

**Do not change** illustrative SemVer examples (e.g. `0.1.0` → `0.1.1` in bump tables), `versionCode` formula examples, git history, or historical delivery notes that intentionally refer to an older milestone.

**How to find stragglers** after editing the table above:

```bash
rg '0\.1\.[0-9]+|v0\.1\.[0-9]+' --glob '*.md' --glob '!AGENTS.md'
```

Replace any hit that describes **today’s** build, not an example or historical fact. If a new doc pins the app version, add it to the table in this section.

`docs/PRIVACY.md` **Effective date** changes only when policy content changes — not on every version bump.

### When to bump (default — no need to ask the user)

| Change type | Bump | Example |
|-------------|------|---------|
| Bug fix, crash fix, regression fix | **PATCH** | `0.1.0` → `0.1.1` |
| Small UX polish, copy, non-breaking tuning | **PATCH** | `0.1.1` → `0.1.2` |
| New feature or user-visible capability | **MINOR** | `0.1.2` → `0.2.0` |
| Refactor with no user-visible change | **PATCH** (or none) | — |
| Breaking preference/API/behavior change (alpha) | **MINOR** | `0.2.0` → `0.3.0` |
| Docs-only, CI-only, agent-only | **none** | — |
| First Play Store production release | **major to `1.0.0`** | `0.9.0` → `1.0.0` |
| Breaking change after `1.0.0` | **MAJOR** | `1.2.3` → `2.0.0` |
| New feature after `1.0.0` | **MINOR** | `1.0.0` → `1.1.0` |
| Fix after `1.0.0` | **PATCH** | `1.1.0` → `1.1.1` |

**Alpha (`0.x.y`):** reset PATCH to `0` when bumping MINOR (`0.1.4` → `0.2.0`).  
**Stable (`1+`):** same SemVer rules; reset PATCH on MINOR, reset MINOR+PATCH on MAJOR.

### `versionCode` (Android)

- Must **strictly increase** for every Play Console upload.
- Rule: `versionCode = major * 10_000 + minor * 100 + patch` while `major == 0` or `major == 1`.
  - `0.1.0` → `100`
  - `0.1.1` → `101`
  - `0.2.0` → `200`
  - `1.0.0` → `10_000`
  - `1.2.3` → `10_203`
- If the formula would decrease `versionCode`, use `previousVersionCode + 1` instead.

### GitHub releases

Pushing to `main` with a `versionName` change in `app/build.gradle.kts` triggers `.github/workflows/release.yml`, which creates tag `v{versionName}` and a GitHub Release with auto-generated notes (e.g. `v0.1.3`). Do **not** create the tag manually unless re-cutting a failed release.

`workflow_dispatch` on the same workflow re-checks the current `versionName` and creates the release if the tag is still missing (useful for backfill).

### Current version

**`0.1.3`** (`versionCode` **103**).

## Build & verify

```bash
./gradlew detekt test assembleRelease
```

Instrumented tests (emulator): `./gradlew :testing:audio-harness:connectedDebugAndroidTest :app:connectedDebugAndroidTest`

## Scope notes

- **CI:** GitHub-hosted emulators only (`ci.yml`, `instrumented-matrix.yml`, `release.yml`). No self-hosted device runners.
- **YouTube validation:** Manual spike checklist (`docs/spike-youtube-checklist.md`) before Play promotion — not automated.
- **License:** Apache 2.0. **Privacy:** `docs/PRIVACY.md`.

## Do not

- Bump version for docs-only or agent-instruction-only changes unless the user explicitly asks.
- Introduce Play Store signing / upload steps unless requested.
- Add self-hosted hardware CI or nightly device regression infrastructure.