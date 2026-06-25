# DeciBoost Privacy Policy

**Effective date:** 2026-06-25  
**Version:** 1.0.0-alpha

## Summary

DeciBoost is a local-first Android app. In v1 it does **not** collect, transmit, or sell personal information. There is no analytics SDK, no crash reporting backend, and no account system.

## Data we process (on your device only)

| Data | Purpose | Stored | Shared |
|------|---------|--------|--------|
| Boost level, volume, settings toggles | App functionality | Local DataStore on device | Never to DeciBoost (optional OS backup — see below) |
| Onboarding completion flag | Skip repeat onboarding | Local DataStore | Never to DeciBoost (optional OS backup — see below) |
| Optional device fingerprint hash | Effective max-gain migration | Local DataStore | Never to DeciBoost (optional OS backup — see below) |
| Microphone samples (optional) | Waveform visualizer only | In-memory; not saved | Never |

All preferences stay on your device via Android **DataStore**. Uninstalling the app removes this data.

## Android backup

The app manifest sets `android:allowBackup="true"`, which lets Android's **optional** device backup (e.g. Google account backup on Pixel devices) include app data. If you enable Android backup on your device, boost settings in DataStore may be copied to your backup provider as part of normal Android backup — not to DeciBoost servers. DeciBoost does not initiate or control backup; it is handled entirely by the Android OS and your device settings. Uninstalling the app removes local data; backup copies follow your provider's retention policy.

## Viewing this policy in the app

In v1.0.0-alpha, **About → Privacy policy** opens this document on GitHub (`main` branch). A hosted or in-app policy page may replace this link before Play Store release.

## Permissions

- **Notifications** — required for the foreground service while boost is active.
- **Battery optimization exemption (optional)** — recommended during onboarding for background reliability.
- **RECORD_AUDIO (optional)** — only if you enable the waveform visualizer. Audio is used for real-time display only; it is not recorded, uploaded, or stored.

DeciBoost does **not** request network access for core functionality in v1.

## What we do not collect

- Name, email, phone number, or account credentials
- Location
- Contacts, photos, or files
- Media playback history or app usage analytics
- Advertising identifiers

## Third parties

DeciBoost v1 does not integrate third-party analytics, advertising, or social SDKs. Boost applies to audio from other apps on your device; DeciBoost does not access those apps' content beyond standard Android audio-effect APIs.

## Children's privacy

DeciBoost is not directed at children under 13. We do not knowingly collect personal information from anyone.

## Changes

Material changes to this policy will be reflected in this repository and noted in release notes.

## Contact

- **Email:** a.ayv4zyan@gmail.com
- **Issues:** [github.com/ayv4zyan/DeciBoost/issues](https://github.com/ayv4zyan/DeciBoost/issues)