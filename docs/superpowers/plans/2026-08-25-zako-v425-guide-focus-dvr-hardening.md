# Zako v4.25 Guide, Focus, DVR, and Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a permanently signed Zako 4.25 test build that fixes Movies/Series D-pad focus, improves guide readability and navigation, adds a lightweight grid guide and movie Info, exposes live DVR controls on Android touch devices, and hardens recording/memory behavior.

**Architecture:** Preserve the existing v4.24 build-time patch chain and add one final v4.25 patcher plus static verification checks. Reuse the existing `EpgStore`, `Playback`, `Timeshift`, `RecordingService`, and provider stream budget; do not add another player/tuner or bulk-fetch metadata. Keep the branch isolated from `main` until device testing passes.

**Tech Stack:** Kotlin, Jetpack Compose, Media3/ExoPlayer, Coil, OkHttp, Python source patch/verification scripts, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-25-zako-v424-guide-focus-dvr-hardening-design.md`

## Global Constraints

- Fire TV remains the primary performance target.
- Remote D-pad navigation must be deterministic.
- No extra player, tuner, or duplicate provider stream for guide/DVR UI.
- Keep the existing near-term EPG memory window; no week-long in-RAM guide.
- Movie details are lazy, fetched only when Info is opened.
- Do not merge to `main` in this pass.
- Version becomes 4.25 / code 50 and remains signed with the permanent Zako certificate.

---

### Task 1: Deterministic Movies/Series and Live focus

**Files:**
- Create: `tools/apply_v425.py`
- Create: `tools/verify_v425.py`
- Modify at build time: `app/src/main/java/com/easyiptv/player/MainActivity.kt`

**Interfaces:**
- Consumes: v4.24 patched `MoviesPane`, `SeriesPane`, `LivePane`.
- Produces: no whole-content Left fallback; first-column-only rail exit; live first/last wrap.

- [ ] Write static verification assertions for removed broad Left fallback and added live wrap markers.
- [ ] Run verifier before v4.25 patch and confirm it fails.
- [ ] Patch Movies/Series so the grid owns Left/Right and only column 1 exits to categories.
- [ ] Add per-row live focus requesters and Up/Down wraparound.
- [ ] Run verifier and confirm focus assertions pass.

### Task 2: Readability, Mini Guide EPG, and download color

**Files:**
- Modify at build time: `MainActivity.kt`

**Interfaces:**
- Consumes: `EpgStore.guide(epgId, channelName)`, Mini Guide visible rows.
- Produces: `ProgramCyan`, `DownloadGreen`, brighter secondary text, visible current-program/time information.

- [ ] Add verifier assertions for palette markers and Mini Guide program lookup.
- [ ] Brighten secondary text and category labels while retaining pink focus.
- [ ] Make download icons bright green.
- [ ] Expand each visible Mini Guide row enough for channel + current program + start/end time.
- [ ] Make current row/program/times and Previous program readable from couch distance.
- [ ] Run verifier.

### Task 3: Lightweight full Live grid guide

**Files:**
- Modify at build time: `MainActivity.kt`

**Interfaces:**
- Consumes: filtered live channels, `EpgStore`, `ScheduleStore`, existing `onPlayLive`.
- Produces: `LiveGridGuide` and a `GRID GUIDE` entry point.

- [ ] Add verifier assertions that the guide reuses `EpgStore` and contains no new player/provider networking.
- [ ] Add a grid/list toggle at the top of Live TV.
- [ ] Implement a lazy vertical channel list with a bounded horizontal program/time window.
- [ ] Show an action dialog on a program: Watch/Info/Record now or Info/Schedule future recording when available.
- [ ] Keep the existing individual-channel schedule button.
- [ ] Run verifier.

### Task 4: Lazy movie Info

**Files:**
- Modify: `app/src/main/java/com/easyiptv/player/Data.kt`
- Modify at build time: `MainActivity.kt`

**Interfaces:**
- Produces: `MediaInfo` and `Source.mediaInfo(movieId)` with Xtream override; M3U default returns null.
- Consumes: movie ID only when Info is opened.

- [ ] Add `MediaInfo` model and default `Source.mediaInfo` method.
- [ ] Implement Xtream `get_vod_info&vod_id=...` parser with tolerant plot/year/rating/genre fields.
- [ ] Add poster/title `ⓘ` action that is touch-clickable and does not steal ordinary D-pad horizontal focus.
- [ ] Add lazy Info dialog with Play, Download, Close.
- [ ] Verify no catalog-wide info fetch loop exists.

### Task 5: Android live DVR touch controls

**Files:**
- Modify at build time: `MainActivity.kt`

**Interfaces:**
- Consumes: `Playback.seekDvrBy`, current ExoPlayer pause/play, existing record-choice path.
- Produces: touch-only compact REW / PLAY-PAUSE / FF / LIVE / REC row.

- [ ] Detect a touch-capable Android device using the Android configuration touchscreen flag.
- [ ] Tap on live video toggles the touch DVR controls; auto-hide after inactivity.
- [ ] Bind controls to the same DVR/recording functions used by Fire TV.
- [ ] Ensure Fire TV remote path is unchanged.
- [ ] Run verifier.

### Task 6: Recording and memory hardening

**Files:**
- Modify at build time: `MainActivity.kt`

**Interfaces:**
- Consumes: existing `RecordingService` and `Recorder.activeName`.
- Produces: IO-thread file scanning, slower refresh, no unnecessary corner preview SurfaceView while recording, memory trim cache release.

- [ ] Move initial and active-recording directory scans/sorts to `Dispatchers.IO`.
- [ ] Slow active recording refresh to about 5 seconds.
- [ ] Update local file state after delete instead of rescanning synchronously.
- [ ] Suppress the home corner `PlayerView` while recording is active.
- [ ] Add `onTrimMemory` to clear disposable Coil memory cache on low-memory signals.
- [ ] Run verifier.

### Task 7: Version, workflows, signed release verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/build.yml`
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Produces: version 4.25 / code 50, debug CI artifact for diagnostics, permanent-signed release artifact for installation.

- [ ] Bump versionCode 49 -> 50 and versionName 4.24 -> 4.25.
- [ ] Run the existing v4.24 patch chain then `tools/apply_v425.py` and `tools/verify_v425.py` in both workflows.
- [ ] Rename artifacts to v4.25.
- [ ] Let GitHub Actions build both workflows.
- [ ] Inspect release job steps and logs.
- [ ] Confirm release manifest is not debuggable and does not request DUMP.
- [ ] Confirm permanent certificate SHA-256 equals `8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165`.
- [ ] Download the signed GitHub artifact and provide it for device testing.
