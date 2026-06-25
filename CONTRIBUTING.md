# Contributing to DeciBoost

Thank you for your interest in DeciBoost!

## Getting started

1. Fork the repository on GitHub.
2. Clone your fork and create a branch from `main`:
   ```bash
   git checkout -b feature/my-change
   ```
3. Open the project in Android Studio (JDK 21, Android SDK with API 36).

## Before you submit a PR

Run these checks locally:

```bash
./gradlew test detekt assembleDebug
```

If your change touches instrumented tests or the audio harness:

```bash
./gradlew :testing:audio-harness:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

(Requires a running emulator or connected device.)

## Pull request guidelines

- Keep changes focused; one logical change per PR.
- Describe **what** changed and **why**.
- Note which tests you ran and their results.
- Follow existing code style and module boundaries (`:core`, `:feature`, `:testing`).
- Do not add network analytics or PII collection without an explicit design discussion.

## Reporting bugs

Open a [GitHub issue](https://github.com/ayv4zyan/DeciBoost/issues) with:

- Device model and Android version
- DeciBoost version (or commit SHA)
- Steps to reproduce
- Expected vs actual behavior
- Logcat snippets if relevant (`DeciBoostProbe`, `BoostEngine`)

## Code of conduct

Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Be respectful and constructive.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).