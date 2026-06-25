# 16 KB page size (Android 15+)

## v1 status

DeciBoost **v1 ships no native libraries** (JNI/NDK). Audio boosting uses platform Java/Kotlin APIs (`LoudnessEnhancer`, `DynamicsProcessing`, `AudioManager`).

The app module sets AGP packaging defaults for future native deps:

```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = false
    }
}
```

(`app/build.gradle.kts`)

## When adding native code

1. Build native artifacts with **16 KB ELF alignment** (NDK r27+; linker flag `-Wl,-z,max-page-size=16384` where required).
2. Verify APK/AAB before release:

   ```bash
   # List .so alignment (expect 0x4000 / 16384 for 16 KB-ready libs)
   unzip -l app-release.apk 'lib/*/*.so'
   ```

3. Test on a **16 KB page size** emulator or device image (Android 15 system images marked 16 KB).
4. Keep `useLegacyPackaging = false` unless a third-party SDK documents otherwise.

## CI recommendation

When the first `.so` is introduced, add a release-build check (e.g. `readelf -l` or Play pre-launch report). v1.0.0-alpha has no native libs; `./gradlew assembleRelease` is sufficient for current releases.