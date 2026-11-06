# Shuffle & Queue Order Model (`ShuffleModel`)

## 1. Current Model
- **`ShufflePlan.kt:20-146`**:
  - `shuffledTail(order, fromIndex, random)` (`L23-29`): permutes only `subList(fromIndex, size)` while keeping `0 until fromIndex` (played history + current track) untouched at their existing slots.
  - `restore(liveIds, snapshotIds)` (`L36-47`): filters `snapshotIds` to surviving live keys and appends keys added while shuffled.
  - `sliceAroundKeep(beforeCount, currentIdx, reordered, keepId, key)` (`L113-143`): splits `reordered` into `head` (`0 until targetIndex`) and `tail` (`targetIndex + 1 until size`) around `keepId`.
- **`PlayerManager.kt:1386-1468`**:
  - `setShuffle(enabled)` (`L1386-1412`): sets `from = _currentIndex.value + 1` (`L1396`), snapshots `live.map { it.queueId }` into `shuffleOrder` (`L1398`), computes `shuffledTail` or `restore` on `Dispatchers.Default` with an `O(N^2)` `live.firstOrNull` scan (`L1401-1408`), and calls `applyOrder` (`L1409`).
  - `applyOrder` (`L1427-1468`): mutates `MediaController` via up to **4 IPC timeline operations** (`removeMediaItems` suffix `L1441`, `removeMediaItems` prefix `L1445`, `addMediaItems` head `L1454`, `addMediaItems` tail `L1463`).
  - `move` (`L1078`) and `commitOrder` (`L1124`): overwrite `shuffleOrder = next.map { it.queueId }` on manual drag-reorder.

## 2. Problems
1. **Current song not on top on Shuffle ON (`PlayerManager.kt:1396`, `ShufflePlan.kt:23-29`, `FINDINGS-VIDEOS.md` #4 vs #5)**: `shuffledTail` uses `from = _currentIndex.value + 1`, keeping `currentIndex > 0` instead of moving the playing track to `#1` (index `0`) as in the Metrolist reference (`FINDINGS-VIDEOS.md` #5).
2. **Tap after shuffle / index corruption on >100 items (`PlayerManager.kt:1234, 1256`, `BoundedQueuePlayer.kt:98-111`, `FINDINGS-A.md` Cause 1)**: `PlayerService.kt:299-300` wraps `ExoPlayer` in `BoundedQueuePlayer(maxWindow = 100)`. Passing full-queue indices through `MediaController` double-offsets seeks or throws `IndexOutOfBoundsException` when mutating indices `>= 100`.
3. **Unshuffle fails to restore original order (`PlayerManager.kt:1078, 1124, 1441-1463`)**: manual drag-reorder overwrites `shuffleOrder`, and `applyOrder`'s 4-step `MediaController` IPC fails against `BoundedQueuePlayer`'s 100-item window or leaves a truncated queue if cancelled mid-flight.

## 3. Target Model
- **Single source of truth**: `ShuffleModel` is an immutable value state owned by `PlayerManager`. Controller `shuffleModeEnabled` is always `false`; the displayed queue order equals the actual playback order.
- **Section A compatibility (`FINDINGS-A.md`)**: `currentQueueId` is derived strictly from the active `MediaItem`'s `EXTRA_QUEUE_ID` (`TrackMediaItems.queueIdOf(c.currentMediaItem)`) via `syncCurrentFromPlayer`, never from window-relative `c.currentMediaItemIndex`.
- **Invariants**:
  - When `shuffleEnabled == false`: `originalIds` is `emptyList()` and `displayedIds` holds the active catalog/playback order.
  - When `shuffleEnabled == true`: `originalIds` holds the pre-shuffle `queueId` sequence (plus edits), and `displayedIds` is a permutation of the exact same `queueId` set.

## 4. Data Structures & Bounded Persistence
```kotlin
data class ShuffleState(
    val displayedIds: List<Long> = emptyList(), // Live playback & UI order of stable queueIds
    val originalIds: List<Long> = emptyList(),  // Pre-shuffle order; empty when shuffleEnabled == false
    val currentQueueId: Long? = null,           // Derived from active MediaItem EXTRA_QUEUE_ID
    val shuffleEnabled: Boolean = false,
)
```
- **Stable Queue Item IDs**: Each queued `Track` is wrapped in `QueueItem(song: Track, queueId: Long)` minted from monotonic `queueSeq++` and stored in `MediaItem.mediaMetadata.extras["queue_id"]`.
- **Bounded Off-Main-Thread Persistence (`QueueStore.kt:100-186`, `PlayerManager.kt:581-624`)**:
  - `QueueSnapshot` caps serialized `tracks: List<PersistedTrack>` to at most `MAX_PERSISTED_TRACKS = 2000` items centered around `currentIndex` (plus `shuffleOrder` filtered to that window) so queues up to 2,000 tracks (such as a 906-song Downloads queue) are persisted and restored 100% intact without truncation while still bounding 5,000+ item queues on `Dispatchers.IO`.
  - All snapshot JSON encoding and `store.edit` writes run on `Dispatchers.IO` via throttled `persistAsync(debounceMs = 500L)` (and `savePosition()` for the single `Long` position key). `PlayerService.onTaskRemoved`/`onDestroy` fires an asynchronous `CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { playerManager.persist() }` instead of blocking the main thread with `runBlocking`.
  - Legacy snapshots with `queueId == -1L` mint sequential IDs on load (`PlayerManager.kt:469`); if `shuffleActive && shuffleOrder.isEmpty()`, `originalIds` defaults to `displayedIds`.

## 5. Pure Functions (`ShuffleModel`)
All operations in `ShuffleModel` (replacing `ShufflePlan`) are pure, `O(N)` using `HashSet`/`HashMap` indices, and operate on stable `queueId: Long`:
- **`shuffle(state, random)`**:
  - Captures `orig = if (state.shuffleEnabled && state.originalIds.isNotEmpty()) state.originalIds else state.displayedIds`.
  - If `state.currentQueueId != null && state.currentQueueId in state.displayedIds`: places `currentQueueId` at index `0` (`#1`) and shuffles all other items (`listOf(currentQueueId) + state.displayedIds.filterNot { it == currentQueueId }.shuffled(random)`).
  - Else: `newDisplayed = state.displayedIds.shuffled(random)` with `currentQueueId = newDisplayed.firstOrNull()`.
  - Returns `state.copy(displayedIds = newDisplayed, originalIds = orig, currentQueueId = currentQueueId, shuffleEnabled = true)`.
- **`unshuffle(state)` & Explicit Fallbacks**:
  - Builds `liveSet = state.displayedIds.toHashSet()`. Computes `survivingOrig = state.originalIds.filter { it in liveSet }` and `orphans = state.displayedIds.filterNot { it in state.originalIds.toHashSet() }` (tracks added while shuffled via `addToQueue`/radio).
  - **Restored order & slot resolution**:
    1. *Normal (`currentQueueId in survivingOrig`)*: `restored = survivingOrig + orphans`. `targetIndex = restored.indexOf(currentQueueId)`. If prior tracks were deleted while shuffled, `targetIndex` naturally shifts to the compacted slot in `survivingOrig`.
    2. *Orphan current track (`currentQueueId != null && currentQueueId in orphans`)*: inserts `currentQueueId` at the anchor slot where shuffle began (clamped to `0..survivingOrig.size`) and appends remaining `orphans` at the end, so `targetIndex` is valid and deterministic.
    3. *Missing/removed current track (`currentQueueId == null || restored.indexOf(currentQueueId) == -1`)*: `restored = (survivingOrig + orphans).ifEmpty { state.displayedIds }`, clamping `targetIndex = 0` and `newCurrentId = restored.firstOrNull()` (never returning `-1` or crashing `subList`).
  - **Played vs Upcoming semantics**: Unshuffle restores **catalog order** relative to the active track at `targetIndex = k` (matching Metrolist `FINDINGS-VIDEOS.md` #5), not play-history order: rows `0 until k` become prior catalog tracks (rendered dimmed `isPlayed = true` and skipped unless user seeks back), row `k` continues playing uninterrupted, and rows `k + 1 until restored.size` are upcoming (`isPlayed = false`).
- **`tap / select(state, tappedQueueId)` & Seek Contract (`FINDINGS-A.md`)**:
  - Pure state: returns `state.copy(currentQueueId = tappedQueueId)` with `displayedIds`, `originalIds`, and `shuffleEnabled` untouched.
  - Player seek contract: `playAt(index)` (`PlayerManager.kt:1244`) and `playOrToggle(queueId)` (`L1283`) route all seeks through the single helper `seekToQueueIndex(c, fullIndex, 0L, fromIndex)` (`L556-579`), which translates `fullIndex` into window-relative step indices via `QueueWindow.seekSteps` so `BoundedQueuePlayer.seekTo` (`BoundedQueuePlayer.kt:98-111`) never double-adds `windowStart()`.
- **`playNext(state, newQueueIds)`**: Inserts `newQueueIds` immediately after `currentQueueId` in `displayedIds`, and if `shuffleEnabled`, immediately after `currentQueueId` in `originalIds` (or at index `0` if `currentQueueId` is absent).
- **`addToQueue(state, newQueueIds)`**: Appends `newQueueIds` to `displayedIds` and, if `shuffleEnabled`, to `originalIds`.
- **`remove(state, removedQueueId)`**: Removes `removedQueueId` from `displayedIds` and `originalIds`; if `removedQueueId == currentQueueId`, advances `currentQueueId` to the next item at the same index in `displayedIds` (or new last item, or `null` if empty).
- **`reorder(state, newDisplayedIds)`**: Validates permutation of `displayedIds`, updates `displayedIds = newDisplayedIds`, and **leaves `originalIds` untouched** (fixing `PlayerManager.kt:1078, 1124`).
- **`duplicates`**: Identical `Track.id` entries receive distinct `queueId`s; all operations key strictly on `queueId`.

## 6. Atomic Player Application & 5,000-Item Performance
- **In-Process `ExoPlayer` Bridge (`PlayerService.kt:99, 298-300`, `PlayerManager.kt`)**:
  - `PlayerManager` currently holds only a `MediaController` bound to `BoundedQueuePlayer(maxWindow = 100)`. To mutate full timelines (`> 100` items) without `BoundedQueuePlayer` coordinate clipping or Binder `TransactionTooLargeException`, `PlayerService` registers its live `ExoPlayer` instance with same-process `@Singleton PlayerManager` via `playerManager.attachLocalPlayer(exo)` in `onCreate` (`PlayerService.kt:298`) and clears it via `playerManager.detachLocalPlayer(exo)` in `onDestroy` (`L328`).
  - `BoundedQueuePlayer(maxWindow = 100)` stays wrapping `exo` inside `MediaLibrarySession` for external controllers (Android Auto, Bluetooth AVRCP, system notification).
- **`O(N)` Pre-Computation on `Dispatchers.Default`**:
  - Before timeline mutation, `setShuffle` indexes `val byId = live.associateByTo(HashMap(live.size)) { it.queueId }` and resolves `reordered` + `MediaItem`s in `O(N)` time (replacing the `O(N^2)` `live.firstOrNull` scan at `PlayerManager.kt:1405`).
- **Latest-Wins Coalescing + `NonCancellable` Atomic Apply**:
  - Rapid toggles increment a `shuffleGeneration: AtomicLong` and cancel any prior `shuffleJob` **while it is still in the `Dispatchers.Default` planning phase**.
  - Once planning finishes, the timeline mutation + `_queue`/`_currentIndex`/`_shuffle` state commit executes inside `withContext(NonCancellable + Dispatchers.Main.immediate)` on the in-process `ExoPlayer` (checking `gen == shuffleGeneration.get()` before starting). Because the mutation runs synchronously on `Main.immediate` under `NonCancellable`, a rapid toggle can never cancel `applyOrder` mid-way or orphan a truncated queue.
  - On `exo`, `applyOrder` locates the playing item's live index `curIdx` (`0..exo.mediaItemCount - 1`), removes suffix `(curIdx + 1, exo.mediaItemCount)` and prefix `(0, curIdx)`, and inserts `headMediaItems` at `0` and `tailMediaItems` at `targetIndex + 1` in a single uninterruptible main-thread frame. The active item at `curIdx` is never removed or re-prepared, so audio playback never restarts or glitches.

## 7. Stateless Detail Header Shuffle Action
- The Album and Playlist header **Shuffle** button (`DetailScreens.kt:160, 463`) is a **stateless queue-replacing action** (not a toggle indicator): tapping it always calls `playAll(tracks, shuffle = true, source = name)`, which mints fresh `QueueItem`s, sets `originalIds` to the catalog order, sets `displayedIds` to a full permutation (`startIndex = 0`), and sets `PlayerManager.shuffleEnabled = true`.
- Tapping **Play** (`DetailScreens.kt:159, 462`) or a track row (`L179, 507`) calls `playAll(..., shuffle = false)`, resetting `shuffleEnabled = false` and `originalIds = emptyList()`.

## 8. Repeat All
- Repeat mode is delegated to `ExoPlayer.repeatMode` (`REPEAT_MODE_OFF`, `REPEAT_MODE_ALL`, `REPEAT_MODE_ONE`). With `REPEAT_MODE_ALL`, reaching the end of `displayedIds` wraps to index `0` of `displayedIds` without reshuffling or altering `originalIds`.

## 9. UI Contract
- **Highlight & Indicator**: `QueueList` (`NowPlayingScreen.kt:1972, 2043, 2621`) resolves `currentQueueId` from the active item and computes `QueueHighlight.state(displayIds, idx, currentQueueId)`. On Shuffle ON, the playing song moves to row `0` (`#1`); rows below are upcoming (`isPlayed = false`). On Shuffle OFF, the playing song moves to its restored catalog slot `k`, rows `0 until k` dim (`isPlayed = true`), and rows `k + 1 until N` render as upcoming.
- **Stateful Transport Shuffle Toggles**: `ControlsRow` (`NowPlayingScreen.kt:1145, 1157-1159`) and `QueueBottomBar` (`NowPlayingScreen.kt:2180-2218, 2642-2645`) observe `player.shuffleEnabled` and reflect `ShuffleState.shuffleEnabled` immediately.

## 10. Test Plan
1. **Unit & Property Tests (`ShuffleModelTest`)**:
   - *Shuffle ON/OFF & Metrolist parity*: `displayedIds.first() == currentQueueId` on ON; `unshuffle(shuffle(state)).displayedIds == state.displayedIds` with `currentQueueId` back at its catalog slot `k`.
   - *Unshuffle fallbacks*: deleted prior tracks shift `k` cleanly; orphan `currentQueueId` (added while shuffled) and removed/missing `currentQueueId` (`indexOf == -1`) clamp to valid `targetIndex` without throwing.
   - *Tap, edits & duplicates*: `select(state, id)` preserves `displayedIds`/`originalIds`; `playNext`, `addToQueue`, `remove`, `reorder` (drag leaves `originalIds` intact) preserve invariants with duplicate `Track.id`s.
2. **Concurrency, Window-Boundary & Scale Tests (`QueueShufflePipelineTest`, `BigPlaylistShuffleTest`)**:
   - *Rapid-toggle cancellation*: 20 rapid `setShuffle` toggles in parallel never truncate `exo.mediaItemCount` or drop items; final state matches the last toggle.
   - *100-item window boundaries*: shuffle ON/OFF and `select()` seeks across indices `0, 49, 50, 99, 100, 101, 905, 4999` land on the exact `queueId` without double-offsetting `windowStart()`.
   - *5,000-item scale*: `O(N)` shuffle ON/OFF completes in `< 50ms` on JVM without restarting the active item, and snapshot persistence caps at `2000` items off the main thread.

## 11. EGM Review Log
- **1. [BLOCKER] Timeline mutation vs 100-item `BoundedQueuePlayer` window & races**: Verified `PlayerService.kt:298-300` and `PlayerManager.kt:211-244`; decided on in-process `playerManager.attachLocalPlayer(exo)` bridge for full-timeline mutations, latest-wins generation coalescing, and `withContext(NonCancellable + Dispatchers.Main.immediate)` around prefix/suffix replacement so the current item never restarts and rapid toggles never orphan a truncated queue (Section 6).
- **2. [BLOCKER] Unshuffle state inconsistencies**: Defined explicit `unshuffle` fallbacks for deleted/shifted slots, orphan `currentQueueId` (`!in originalIds`), and missing `currentQueueId` (`indexOf == -1`), and specified catalog-order restoration where `< k` is dimmed (`isPlayed = true`) and `> k` is upcoming (Section 5).
- **3. [MAJOR] Tap/seek double offset**: Specified that `select()` seeks exclusively via `seekToQueueIndex` (`PlayerManager.kt:556-579`, `QueueWindow.seekSteps`) consistent with `FINDINGS-A.md` (Section 5).
- **4. [MAJOR] 5,000-item perf & persistence**: Replaced `O(N^2)` lookup with `O(N)` `HashMap` indexing (`associateByTo`), capped `QueueStore` snapshot persistence to `2000` tracks around `currentIndex` (preserving 906-song queues intact while bounding 5,000+ item queues), and moved all saves to `Dispatchers.IO` (removing main-thread `saveBlocking` ANR risk) (Sections 4, 6).
- **5. [MINOR] Citations, stateless header & tests**: Fixed `NowPlayingScreen.kt` citations (`L1145, 1972, 2043, 2180, 2621`), clarified `DetailScreens.kt:160, 463` header Shuffle is a stateless queue-replacing action, and added rapid-toggle cancellation and 100-item window-boundary tests (Sections 7, 9, 10).
