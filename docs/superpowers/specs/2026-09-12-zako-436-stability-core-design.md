# Zako 4.36 Stability Core Design

## Goal
Reduce random process exits on low-memory Fire TV devices without changing the visible UI, controls, navigation, mini guide, playback features, recording features, download features, or catalog behavior.

## Constraints
- Preserve the Zako 4.35 visual design and user-facing feature set.
- Target Android/Fire TV with roughly 1 GB RAM and D-pad-only navigation.
- Keep one primary Media3 player and the existing DVR/recording/download model; do not add multiview or a second permanent player.
- Do not increase Live startup buffering aggressively.
- Avoid a full UI rewrite.
- Keep 4.35 as the working updater target until 4.36 passes CI, signing, and release verification.

## Architecture
### 1. Stability diagnostics
Add a small `StabilityCore.kt` component that installs a process-level uncaught-exception recorder, records low-memory/trim-memory callbacks, captures periodic lightweight heap/process snapshots, and keeps a small rolling log in app-private storage. The log records the last screen/action, playback state, and memory level so a future random exit can be diagnosed instead of guessed.

### 2. Resource/session supervision
Add one owner for long-lived background work. It tracks named coroutine jobs and replaces/cancels earlier jobs when a new load for the same purpose begins. MainActivity lifecycle hooks will notify the stability core and cancel nonessential catalog work when the app is backgrounded or memory pressure is high. Playback remains the single player owner and its existing release path is preserved.

### 3. Disk-backed catalog index
Add a built-in SQLite catalog sidecar (no new external database dependency) that stores Movies and Series rows per playlist. Provider refreshes still use the current APIs, but once parsed they are persisted to SQLite and the app may release the temporary giant response objects. Search/category reads can be served from indexed disk rows instead of repeatedly rebuilding giant in-memory scans. The first 4.36 integration keeps the same `AppData` interface so the UI remains unchanged while reducing duplicate parse/cache overhead; follow-up paging can be added only if Fire Stick measurements show it is still needed.

## Data flow
- App start: install diagnostics -> load lightweight cached/live state -> start Live normally.
- Enter Movies/Series/Search: supervisor starts one catalog load; duplicate/restarted loads replace the previous job instead of stacking.
- Provider response: parse sequentially as today -> persist Movie/Series rows to SQLite -> merge into current AppData -> record a memory snapshot.
- Low-memory callback: log event, cancel nonessential catalog refresh work, trim in-memory diagnostic buffers, leave active Live playback alone.
- Fatal exception: append compact crash record synchronously, then delegate to Android's previous uncaught-exception handler.

## Failure handling
- SQLite failures are non-fatal; provider loading continues and UI behavior stays unchanged.
- Diagnostic writes are best-effort and bounded; logging must never crash the app.
- Resource supervisor cancellation is scoped to background/catalog jobs and must not cancel active recording or download work.
- Existing Playback release behavior remains authoritative for player/decoder cleanup.

## Verification
- TDD verifier must fail before the 4.36 patch and pass after it.
- Verify versionCode/versionName, stability markers, lifecycle hooks, SQLite index creation, and resource-supervisor usage.
- Build a signed release APK in GitHub Actions.
- Verify APK identity/signature, package generated source, publish release, then update `latest.json` only after the release succeeds.
- Do not claim the random-exit issue is solved until the build is green; after release, real Fire Stick soak testing remains the final behavioral validation.
