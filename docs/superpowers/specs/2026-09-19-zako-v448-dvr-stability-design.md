# Zako 4.48 DVR and Stability Design

## Goal
Make Zako feel like a cable box: reliable future recordings, continuous rolling live-TV rewind while the viewer stays on a channel, deterministic remote navigation, and no regressions to the stable playback/catalog/download paths.

## Decisions
- No cloud DVR. Zako has no backend. DVR is local; writable USB is extended local DVR storage and internal app storage is fallback.
- Keep Smooth/direct playback as the known-good fallback.
- DVR Live uses the existing segmented disk-backed TimeshiftRing. The ring spans EPG program boundaries and rolls by time/storage, not by show.
- Timeshift is disposable history; saved recordings are permanent files.
- Same-channel recording consumes the existing timeshift/provider stream. Different-channel recording uses another provider connection only within the configured 1/2/3 stream budget.
- Future scheduling must never crash when exact-alarm capability is unavailable.

## Scheduled Recording
Introduce a scheduler boundary instead of calling AlarmManager directly from UI/storage code. On Android versions where exact alarms require special access, inspect capability before exact scheduling. If exact scheduling is unavailable, return a user-facing state and provide the Android-approved permission/settings path rather than throwing. Preserve start/end padding and persist schedules before scheduling. Reschedule persisted future recordings after boot/app startup where platform behavior requires it.

## Rolling Live DVR
The segmented TimeshiftRing begins when DVR Live tunes successfully and continues until channel change, DVR shutdown, storage failure, or app lifecycle policy stops it. EPG changes do not reset the ring. Timeline bounds come from ring oldest/newest timestamps/virtual offsets, not current-program start/end.

USB target: up to the existing eight-hour bounded history subject to storage safety. Internal target: the existing bounded 90-minute history subject to storage safety. Old segments roll off without disturbing the current reader.

Player transport supports rewind into available history, pause, resume, jump-to-live, and stepped FF/REW rates 2x/4x/8x/16x/normal. Seeking before oldest retained history clamps visibly to oldest available data.

## Recording Ownership
Recording current channel attaches to ring history and then follows the growing ring without opening a second provider stream. Future/different-channel recording owns an independent provider connection only if budget allows. Downloads yield before live playback. One-stream accounts get an explicit conflict message rather than silent playback destruction.

## UI and Navigation
Mini Guide stays compact (three visible rows) and shows channel plus current program. Live timeline represents retained DVR history, with LIVE at the edge, not just current-show duration. LEFT/RIGHT and media keys operate transport predictably; repeated FF/REW changes speed. Focus remains deterministic and pink; program/search emphasis remains yellow. LEFT from content returns through category/menu hierarchy.

Settings language says Local DVR / Extended DVR Storage, never cloud DVR. Remove stale descriptions of the obsolete single growing timeshift file.

## Architecture
Split responsibilities touched by this release out of the oversized MainActivity where practical:
- RecordingScheduler.kt: scheduling capability, exact/fallback result, cancellation/reschedule.
- Recording.kt: recording service and persistent schedule model only.
- LiveDvrController.kt: timeline/ring-facing DVR state and transport semantics.
- LiveOverlay.kt: presentation and remote action mapping.
- MainActivity.kt: composition/navigation wiring, not DVR algorithms.
Keep TimeshiftRing.kt and LiveStorageManager.kt as the storage core unless tests prove a defect.

## Low-memory and Failure Policy
Fire TV ~1 GB remains a primary target. Use disk-backed segments and bounded 64 KB I/O buffers; never retain DVR media in large RAM collections. USB removal/unwritable storage falls back safely to internal or Smooth. Provider stalls consume buffered history first and use bounded reconnect. Never synchronously rebuild Media3 recursively from an error callback.

## Regression Scope
Before release verify scheduled recording on modern Android capability paths, Fire TV scheduling, cross-program rolling history, pause/rewind/stepped FF/jump-live, channel-change reset, 1/2/3 provider-stream budgets, same/different-channel recording, USB removal, no-USB operation, Smooth fallback, startup input gate, guide/Mini Guide, Movies, Series, Search, downloads/resume, captions, SBS handling, updater identity/signature, and low-memory policy.

## Release
Ship as Zako 4.48 only after CI builds the complete generated source, architecture/regression checks pass, APK package/version/signature are verified, and the release artifact is independently checked. Device behavior remains a separate acceptance gate after CI.
