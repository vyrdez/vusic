# Known Issues (Phase 10.3 Bug Sweep)

This document tracks findings from the Phase 10.3 bug sweep (`FINDINGS-SWEEP.md`).
- Items #1, #4, #8, #12, #13, #15, and #17 were resolved in Task F3-PART1.
- Items #2 and #3 were resolved in Sections A and B.
- Items #5, #6, #7, and #11 were resolved in Task F3-PART2 (P10.3-F).

The remaining deferred items are tracked below with severity, affected files, descriptions, and rationales for deferral.

---

### Fixed in P10.3-F (Task F3-PART2)
- **#5 (S2)**: `PlayerService.resolvePlayRequest` now calls `adoptExternalQueue` before building `MediaItem`s and passes each `QueueItem.queueId` into `resolveTrack(item.song, item.queueId)`, ensuring ExoPlayer `MediaItem`s carry matching `EXTRA_QUEUE_ID` extras for exact duplicate disambiguation.
- **#6 (S2)**: Android Auto "Play radio mix" (`ACTION_RADIO_NODE`) now starts radio via `playerManager.startRadio(seed)` without calling `adoptExternalQueue` (which previously invoked `cancelRadio()` immediately), and resolves the seed track with its minted `queueId`.
- **#7 (S2)**: `PlayerManager` saves `repeatMode = _repeatMode.value` in `persist()`, restores `_repeatMode.value = snapshot.repeatMode` on snapshot load, and sets `p.repeatMode = snapshot.repeatMode` / `c.repeatMode = _repeatMode.value` in `seedQueue()` and `restoreFromSnapshot()`.
- **#11 (S2)**: `PlayerManager` tracks `playJob: Job?` and cancels prior in-flight jobs in `playAll(...)`, `restoreCleared(...)`, and `clear()` to prevent rapid taps from racing over async I/O and clobbering the queue out of order.

---

## Deferred Items

### #9: Incomplete state teardown on user logout
- **Severity**: S2
- **Location**: `app/src/main/java/com/feishin/mobile/ui/settings/SettingsViewModel.kt:244-249` & `app/src/main/java/com/feishin/mobile/ui/navigation/AppNav.kt:79`
- **Description**: `SettingsViewModel.logout()` clears `MetadataCache` and optionally downloads, but does not stop `PlayerManager` or clear `QueueStore`, `CachedTrackStore`, `OfflineSyncStore`, or `LyricsRepository` memory cache. A song from the previous server can continue playing, and offline actions could replay against a new server.
- **Why Deferred**: Cross-cutting logout lifecycle touching 5 separate storage subsystems; requires unified atomic session reset architecture.

---

### #10: Enqueue downloads blocking on Main & missing server URL scope in DB
- **Severity**: S2
- **Location**: `app/src/main/java/com/feishin/mobile/data/download/DownloadRepository.kt:166` & `app/src/main/java/com/feishin/mobile/data/db/MusicDatabase.kt:21`
- **Description**: `enqueueTracks` runs on `Dispatchers.Main` with N sequential Room queries. Additionally, `DownloadEntity` (`songId`) is not scoped by server URL, while disk files live under `downloads/${serverFolder()}`; switching servers can cause ID collisions and unexpected file deletion in `syncKeepGroups`.
- **Why Deferred**: Requires a Room database schema migration (composite key or server URL column) and comprehensive multi-server storage migration.

---

### #14: `globalLookupMutex` in LyricsRepository blocks active song during prefetch
- **Severity**: S2
- **Location**: `app/src/main/java/com/feishin/mobile/data/repo/LyricsRepository.kt:55,113-186,215-220`
- **Description**: `globalLookupMutex` serializes all lyrics network lookups across providers with a 20-second budget. If upcoming track prefetch triggers a slow miss across network providers, user-initiated lookups for the active track are starved.
- **Why Deferred**: Concurrency refactor in `LyricsRepository` requiring preemption/priority queues or dual-mutex isolation to avoid provider rate-limit regressions.

---

### #16: Pausing download leaves active worker transfer running
- **Severity**: S3
- **Location**: `app/src/main/java/com/feishin/mobile/data/download/DownloadWorker.kt:133,294` & `app/src/main/java/com/feishin/mobile/data/download/DownloadRepository.kt:260-268`
- **Description**: `DownloadRepository.pause()` updates Room status but only cancels the worker if the queue is empty. When running with concurrent downloads, active transfers keep streaming until completion, overwriting `status = 'COMPLETED'`.
- **Why Deferred**: Requires adding in-flight cancellation checks to `DownloadWorker.transfer()` read loop and synchronization with `DownloadRepository`.

---

### #18: Widget cold-start credential race & blocking `.get()` call
- **Severity**: S3
- **Location**: `app/src/main/java/com/feishin/mobile/widget/NowPlayingWidget.kt:68` & `app/src/main/java/com/feishin/mobile/widget/WidgetActionReceiver.kt:62,87`
- **Description**: When the app process is cold-started by widget broadcast, `CredentialsStore.preload()` is asynchronous, causing `creds.state.value` to be null on immediate reads. Also, `WidgetActionReceiver` calls `.get(10, TimeUnit.SECONDS)` without handling `TimeoutException`.
- **Why Deferred**: Requires rewriting widget broadcast receivers to handle asynchronous credential initialization and structured timeouts.

---

### #19: MediaStore artist grouping uses 32-bit `hashCode()` collision risk
- **Severity**: S3
- **Location**: `app/src/main/java/com/feishin/mobile/ui/library/DeviceFilesViewModel.kt:213`
- **Description**: `queryMediaStore` computes `artistKey = "local_artist:${artist.hashCode()}"`. String hash codes have collision potential, and tracks with differing album artists assign the album to the first track's artist ID.
- **Why Deferred**: Edge case for local file browsing with negligible user impact; will be addressed in normalized artist key cleanup.

---

### #20: Missing Room fallback migration for schemas v1..4
- **Severity**: S3
- **Location**: `app/src/main/java/com/feishin/mobile/di/DbModule.kt:19-21`
- **Description**: `Room.databaseBuilder` registers `MIGRATION_5_6` but lacks explicit migrations or `.fallbackToDestructiveMigrationFrom(1, 2, 3, 4)`. Users upgrading from legacy builds (schema < 5) encounter an `IllegalStateException`.
- **Why Deferred**: Modifying database builder configuration in `DbModule.kt` risks inadvertent destructive resets without a full matrix of migration test fixtures.

---

### #21: Navigation route double-tap and unencoded URI parameters
- **Severity**: S4
- **Location**: `app/src/main/java/com/feishin/mobile/ui/Nav.kt:421-426,447-451,460-462,476,483-484,499,502`
- **Description**: Detail navigation callbacks lack `launchSingleTop = true` and do not `Uri.encode(id)` on album/artist/playlist routes. Rapid double-taps push duplicate screens, and special characters (`/`, `?`) in Subsonic IDs crash navigation.
- **Why Deferred**: Requires wide navigation layer changes across `AppNav` / `Nav.kt` and testing all route deep-link configurations.

---

### #22: Romanizer classpath vs asset resource loading
- **Severity**: S4
- **Location**: `app/src/main/java/com/feishin/mobile/data/lyrics/Romanizer.kt:58-61`
- **Description**: `ensurePinyinLoaded()` attempts to read `lyrics/pinyin.bin` via Java `ClassLoader.getResourceAsStream`, which fails if the binary is located only in Android `assets/`, causing repeated failed lookups per lyric line.
- **Why Deferred**: Minor CJK romanization fallback issue; does not crash, leak, or affect core playback/lyrics display.
