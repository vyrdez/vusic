# Releasing Vusic

## One-time setup (never commit this)

- `keystore/vusic-release.jks` — the release keystore (gitignored).
- `keystore/keystore.properties` — three lines, no spaces around `=`:
  `storePassword`, `keyAlias`, `keyPassword`. See
  `keystore.properties.example` in the repo root for the format.
- Keep a copy of the keystore and a `SIGNING-INFO.txt` (alias, validity,
  SHA-256 fingerprint — never the passwords) somewhere outside the repository,
  and back it up separately.
- NEVER replace or regenerate the keystore: Android ties updates to the
  signing certificate, so a new key means existing installs can never be
  upgraded in place.

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
6. Write the SHA-256 of each final APK into a `.sha256` file next to it:
   `Get-FileHash <apk> -Algorithm SHA256 | ForEach-Object { $_.Hash.ToLower() + "  " + (Split-Path $_ -Leaf) } > <apk>.sha256`
7. Verify the signature on both splits before publishing:
   `apksigner verify --verbose --print-certs <apk>` must show v2/v3 signing and
   a certificate SHA-256 equal to the one in `SIGNING-INFO.txt`.
8. Tag: `git tag v<version>` on the release commit, push the tag, then create the
   GitHub release and attach the APKs and their `.sha256` files:
   `gh release create v<version> --title "Vusic <version>" --notes-file notes.md <apk> <apk>.sha256 ...`

APKs are **never committed to git** — `/release/` is gitignored. They exist only
as GitHub Release assets, which keeps the clone small and lets you re-upload a
build without a new commit. Screenshot and screen-recording artifacts under
`docs/` are development-only and gitignored for the same reason.
