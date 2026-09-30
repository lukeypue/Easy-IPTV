# RYZOD 4.67 performance investigation

## Locked baseline

Version 4.66, code 90, source `5f19d219abcbb7c594401aa23bdc0ea6ee7b8914`,
is retained on `ryzod-v4.66-locked`. The published APK SHA-256 is
`307d6421ac6cca983c8a1dee026a6f1648b6c0e0f1ec9026d1eac012576d2a7c`.
The update keeps the existing package and permanent signing certificate.

## Findings and scope

The reported remote freeze has a concrete main-thread path: channel changes
probe storage and enumerate/delete the previous ring, while UI snapshot reads
can wait on the same monitor held during disk writes, segment sync and cleanup.
Long histories increase filesystem work. Per-session state was also shared
between retired and current writers, and reader threads could outlive a tune.

The patch retains the existing Media3 player, hardware-first decoder selection,
audio fallback, provider-stream limits, TS synchronization, DVR retention windows,
recording tee, scheduled recording, saved-item menus and updater. It uses one
ingest worker, one disk cleanup worker and two bounded local HTTP readers. It
does not introduce another playback engine or another Fire TV app variant.

Storage work now runs off the UI thread in isolated session directories. UI
snapshots use a published immutable value and retained-byte counter. Temporary
DVR segment closure no longer forces disk durability with fsync. Retired sessions
cannot overwrite the current channel's counters or attach old readers to it.

On low-memory devices, the existing 32 MiB Media3 allocator target takes priority
over duration targets; the redundant in-memory back buffer is zero. Disk DVR
rewind and normal VOD seeking remain available. This is a player allocation
target, not a claim that total application RSS is 32 MiB. Decoder, graphics,
catalog, bitmap and other allocations remain separate. Normal-memory playback
keeps its previous time policy and ten-second back buffer.

Routine diagnostic file writes are bounded and asynchronous. A small separate
crash marker remains best-effort synchronous at fatal process failure. Detached
mini-player views release their player listener/surface references.

Review additionally identified provider ownership during asynchronous setup and
stalled local HTTP clients as required regression cases. Provider ownership must
remain reserved through pending success/failure callbacks; recording must wait
for acknowledged playback release before opening its replacement connection.
Channel changes must close retired sockets so bounded reader workers cannot be
permanently exhausted.

## Launcher investigation

The 4.66 TV test proved Android resource selection under a TV configuration; it
did not prove what Amazon's launcher displays. Amazon's official Fire TV sample
sets `icon`, `logo` and `banner` explicitly on its launch activity. The new path
does the same, explicitly supplies the launch intent icon, and uses a plain wide
bitmap on television configurations. Phone resources retain the adaptive logo.
There are no extra launch activities or aliases. The approved wide artwork is
reused unchanged. Fire OS launcher caching and store-supplied artwork are
separate from package resources, so physical home-screen verification remains
necessary. Do not uninstall or clear app data to test the update.

## External evidence

- Android TV memory guidance describes direct-reclaim stalls and separates
  Java/native/graphics/process budgets:
  https://developer.android.com/training/tv/playback/memory
- Android ANR guidance identifies main-thread I/O and lock contention:
  https://developer.android.com/topic/performance/issues/anr
- Media3 requires player access on its application looper. All player control
  remains there; storage/network work moves away from it:
  https://developer.android.com/media/media3/exoplayer/hello-world
- Amazon sample activity artwork declarations:
  https://github.com/alexa-samples/alexa-sample-fire-tv-app-only-integration/blob/master/app/src/main/AndroidManifest.xml
- Fire OS 8 is based on Android 11 with platform differences:
  https://developer.amazon.com/docs/fire-tv/fire-os-8.html
- TiviMate's official listing establishes its advertised playback features,
  but does not disclose memory budgets or internal local-DVR implementation.
  No internal TiviMate algorithm was assumed or copied:
  https://play.google.com/store/apps/details?id=ar.tvplayer.tv

## Regression evidence

+- Three DVR performance tests failed against 4.66, then passed with the patch.
+- Two actual Media3 allocator tests and the asynchronous logging test exposed
+  the old behavior and passed with their fixes. The initial 25-test suite passed.
+- Four playback/service/socket ownership tests reproduced the review findings
+  before the follow-on fixes. All six ownership cases subsequently passed,
+  including cancelled takeover and failed-header reader lease cleanup.
+- Explicit launcher artwork tests failed against the inherited 4.66 manifest,
+  then passed with plain bitmap TV resources on API 26, 28 and 30. Both phone
+  compatibility tests also passed. The actual optimized 4.66 APK was inspected;
+  it retained the TV artwork and qualifier, but lacked an explicit activity icon.
+- Two cancellation tests timed out against the old EPG implementation while
+  normal short-guide and fallback cases passed. The final CI run enables the
+  cancellable implementation using the same tests.
+- Nine existing safeguard scripts passed on the fully generated candidate.
+- Independent final source review found no remaining Critical/Important issue.
+
+## Verification limits

Automated cases use real ring files, actual Media3 allocator decisions,
Robolectric player/service lifecycle and local TCP fixtures. They can reproduce
main-thread storage/monitor blocking and ownership races. They cannot establish
physical Fire Stick decoder behavior, slow USB kernel I/O, total-process memory,
or the Amazon home-screen cache after an in-place upgrade.

The device acceptance sequence is: install over 4.66, check the home tile;
watch until the DVR history is substantial, then rapidly change channels and
move focus; pause/rewind/resume; start a recording of the watched channel and a
different channel with a one-stream setting; check saved-item play/delete
confirmation; repeat mini-player/full-screen transitions. Retain crash and
stability logs if any step fails. Avoid changes to broad catalog loading in this
release: that needs independent measurement and would widen regression risk.
