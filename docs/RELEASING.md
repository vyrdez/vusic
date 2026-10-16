# Releasing Vusic

## One-time setup (never commit this)

- `keystore/vusic-release.jks` — the release keystore (gitignored).
- `keystore/keystore.properties` — three lines, no spaces around `=`:
  `storePassword`, `keyAlias`, `keyPassword`.
- Back up `D:\Feishin-release-signing\` (keystore copy + `SIGNING-INFO.txt`).
  NEVER replace the keystore: Android ties updates to the signing
  certificate, so a new key breaks updates over existing installs.

## Cut a release

1. Bump `versionCode` (+1, monotonic — it must exceed every shipped build)
   and set `versionName` in `app/build.gradle.kts`. Keep `applicationId`
   (`com.feishin.mobile`) unchanged.
2. If the schema changed: add a Room migration (never destructive
   fallback) plus a migration test. No schema change → nothing to do.
3. Run the full gate, all green:
   `gradlew clean assembleRelease lint test`
   (unit tests, both variants; `lintVitalRelease` must report 0 errors).
4. Audit the APKs in `app/build/outputs/apk/release/`:
   `aapt2 dump badging` (version, not debuggable, permissions, densities),
   `apkanalyzer` (per-ABI `.so`, `assets/dexopt/baseline.prof`, dex size),
   `apksigner verify --verbose --print-certs` on each split — v2+ signed,
   cert SHA-256 matching SIGNING-INFO.txt, NOT the debug certificate.
5. E2E the signed APK on the emulator (`app-x86_64-release.apk`):
   cold start, login (wrong creds → friendly error), cached Home, play,
   mini player, Now Playing, queue (open/drag/swipe/shuffle/save/undo),
   radio, search, Library tabs, favs/cached, downloads + airplane-mode
   offline playback + reconnect, settings, themes, font scale 1.3,
   notification, widget, update-over-install
   (`adb install -r` the same file, session must survive), logcat clean of
   tokens/secrets, zero FATAL/ANR. Record pass/fail/not-verified per item.
6. Copy the finals to `release/` as `Vusic-<version>-arm64-v8a.apk`
   (phones) and the x86_64 build next to it (emulator), each with a `.sha256`
   file. Commit them (the `release/` exception below covers APKs).
7. Tag: `git tag v<version>` on the release commit.

`release/` is intentionally NOT gitignored (small, versioned artifacts);
everything under `*/build/` stays ignored.
