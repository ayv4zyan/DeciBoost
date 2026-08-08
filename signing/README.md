# Release signing

GitHub Release APKs are signed with a **private** keystore that is **not** committed to this repository.

## How CI signs

`release.yml` materializes `github-release.keystore` and `github-release.properties` from GitHub Actions secrets, runs `assembleRelease`, then deletes the files from the runner.

| Secret | Purpose |
|--------|---------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded PKCS12/JKS keystore |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias (currently `github-release`) |
| `RELEASE_KEY_PASSWORD` | Key password (same as store password for PKCS12) |

## Local release builds (maintainers only)

Place the same two files in this directory (they are gitignored):

```text
signing/github-release.keystore
signing/github-release.properties
```

Example properties:

```properties
storeFile=signing/github-release.keystore
storePassword=…
keyAlias=github-release
keyPassword=…
```

Then:

```bash
./gradlew assembleRelease
```

Without these files, `assembleRelease` still builds, but is **not** signed with the official GitHub Release identity. Prefer official APKs from [Releases](https://github.com/ayv4zyan/DeciBoost/releases) or `assembleDebug` for day-to-day work.

## Trust model

- **GitHub Release APKs** after the private-key rotation: signed with a key only available to repo admins / Actions secrets. Signature means “produced by this project’s release pipeline,” not “arbitrary third party with a clone of the repo.”
- **Older GitHub APKs** (signed with the previously public keystore) are a **different** signing identity. In-place update from those builds to new releases may require uninstall + reinstall.
- **Play Store** (if/when): will use a **separate** private upload / app-signing key — never this CI keystore, and never a key committed to git.

## Rotating the key

1. Generate a new keystore (`keytool -genkeypair …`).
2. Update the four `RELEASE_*` secrets (`gh secret set …`).
3. Keep a secure offline backup of the keystore for the maintainer.
4. Note in release notes that sideload users must reinstall (signing identity changed).
