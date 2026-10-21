# Phase 8.4-B — download performance

## What changed
`DownloadWorker.publishProgress()` used to call `dao.setProgress(...)` on every
64 KB chunk. A 5 MB song is ~80 chunks, so a single "keep downloaded" of 89
songs produced ~7 000 Room writes, each of which invalidated the `observeAll()`
flow and recomposed every screen observing it. Two or more concurrent songs
multiplied that, and the foreground notification was rebuilt on every chunk
too.

Changes:
- Room progress writes are gated by a `ProgressThrottle(500 ms, 1 percentage
  point)` (in `core/common/ProgressThrottle.kt`): at most ~2/sec overall and
  only when the percentage actually moved. A short-circuiting `||` in the
  original condition was making the throttle a no-op for percentage changes;
  that is fixed.
- Notification builds are gated by `ProgressThrottle(1000 ms)` and use
  `tryLock()` so a slow `setForegroundAsync` cannot build a backlog of stale
  percentages.
- Enqueue is unchanged: a single `dao.upsertAll(...)` transaction, with
  already-finished songs (file still on disk) skipped.
- Resume-at-start is unchanged: `DownloadStateMachine.isRunnable(...)` keeps
  paused/queued rows runnable and completed rows are skipped.

## What was measured
This project runs the unit tests on the JVM and the emulator. The download
pipeline depends on WorkManager, a foreground service, OkHttp, and a real
server, so the "before" jank metrics from the Phase 8.4 spec (frame times,
recomposition counts, Flow emission counts, notification rate) were not
captured with a profiler during this phase — they come from the original
diagnosis that prompted the fix. The "after" behaviour is verified by:

- `ProgressThrottleTest` (new): a flood of 80 chunk updates inside the
  interval yields a single emit; updates spaced by the interval emit; an
  unchanged percentage does not emit; a new song is not starved by the
  previous one's percentage.
- `DownloadWorker` compile + `DownloadStateMachineTest` / `MobileDataPolicyTest`
  (existing) still pass.
- Manual emulator run: "keep downloaded" of a 20-song album no longer janks the
  library list; the notification updates roughly once per second instead of
  continuously.

## Limits / not verified
- No before/after numbers for 89-song or 300-song batches; the claim of
  "thousands of invalidations" is from the original diagnosis, not a
  re-measurement.
- Real 120 Hz phone behaviour and battery cost were not measured.
- `syncKeepGroups` is still defined but not wired; unchanged from prior phases.
- `ProgressThrottle` is per-`DownloadWorker` instance, i.e. global across the
  concurrent transfers of one run; a separate WorkManager retry gets its own
  instance, which is fine.
