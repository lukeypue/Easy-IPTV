# Zako 4.49 Managed DVR Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add visible/editable/cancelable future recordings, manual timers beyond EPG range, and automatic schedule recovery after app/device restart.

**Architecture:** Extend the existing ScheduleStore as the single persisted source of truth. Add a lightweight schedule recovery receiver and a focused Recordings UI model while preserving AlarmReceiver/RecordingService and all 4.48 playback/resource safeguards.

**Tech Stack:** Android/Kotlin, Jetpack Compose, AlarmManager, BroadcastReceiver, SharedPreferences/JSON, foreground Service, Python structural regression verifiers, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-19-zako-v449-managed-dvr-design.md`

## Global Constraints
- Package remains `com.easyiptv.player`.
- Preserve permanent Zako signing certificate.
- No cloud/backend and no fabricated EPG.
- Preserve configured 1/2/3 provider stream budget and USB/internal storage policy.
- Recovery receiver must not launch MainActivity.
- Device must be powered for recording; closed UI is supported.

## Review Focus
- Reboot/package replacement with multiple future schedules re-arms each without duplicating records.
- Time/timezone changes re-arm future schedules from stored epoch times without deleting them.
- Edit cancels old PendingIntent before persisting/arming replacement.
- Invalid manual ranges (past start or end <= start) never create a timer.
- Cancel removes both alarm and persisted schedule and survives app restart.

---

### Task 1: Schedule management core

**Files:**
- Modify: `app/src/main/java/com/easyiptv/player/Recording.kt`
- Modify: `app/src/main/java/com/easyiptv/player/RecordingScheduler.kt`
- Create: `tools/verify_v449_schedule_core.py`

**Interfaces:**
- Produces: `ScheduleStore.upcoming(prefs)`, `ScheduleStore.edit(...)`, `ScheduleStore.cancel(...)`, `ScheduleStore.rearmAll(context,prefs)`, `ScheduleStore.validateManual(startMs,endMs)`.

- [ ] **Step 1: Write failing verifier**
Check for the five interfaces above, persistence-before-arm ordering, edit cancellation of the prior PendingIntent, and validation `startMs > now && endMs > startMs`.
- [ ] **Step 2: Run verifier; expect FAIL**
`python3 tools/verify_v449_schedule_core.py`
- [ ] **Step 3: Implement minimal core**
Reuse `Sched`, `pending()` and `RecordingScheduler.schedule()`; centralize alarm arming in a private `arm(context,s)`. `rearmAll` iterates only future schedules. `edit` cancels the old alarm, persists replacement, then arms it.
- [ ] **Step 4: Run verifier and 4.48 scheduler verifier; expect PASS**
`python3 tools/verify_v449_schedule_core.py && python3 tools/verify_v448_scheduler.py`
- [ ] **Step 5: Commit**
`git commit -am "feat: add managed DVR schedule core"`

### Task 2: Reboot and clock-change recovery

**Files:**
- Create: `app/src/main/java/com/easyiptv/player/ScheduleRecoveryReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `tools/verify_v449_recovery.py`

**Interfaces:**
- Consumes: `ScheduleStore.rearmAll(context,prefs)`.
- Produces: manifest-declared receiver for boot/package/time recovery.

- [ ] **Step 1: Write failing verifier**
Require `RECEIVE_BOOT_COMPLETED`, receiver declarations/actions for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`; assert receiver calls `rearmAll` and contains no `startActivity`/MainActivity launch.
- [ ] **Step 2: Run verifier; expect FAIL**
`python3 tools/verify_v449_recovery.py`
- [ ] **Step 3: Implement receiver**
On receive, obtain `easyiptv` preferences and call `ScheduleStore.rearmAll(context,prefs)`; do no playback/catalog/UI work.
- [ ] **Step 4: Run verifier; expect PASS**
`python3 tools/verify_v449_recovery.py`
- [ ] **Step 5: Commit**
`git commit -am "feat: recover DVR timers after restart"`

### Task 3: Upcoming recordings and manual timer UI

**Files:**
- Modify generated runtime through new patcher: `tools/apply_v449.py`
- Generated target: `app/src/main/java/com/easyiptv/player/MainActivity.kt`
- Create: `tools/verify_v449_ui.py`

**Interfaces:**
- Consumes: ScheduleStore upcoming/edit/cancel/add/manual validation.
- Produces: Recordings Upcoming UI and Manual Recording dialog.

- [ ] **Step 1: Write failing verifier**
Require visible `Upcoming`, `Manual Recording`, `Edit`, `Cancel`, channel/date/start/end fields, calls to `ScheduleStore.upcoming`, `edit`, `cancel`, and manual validation.
- [ ] **Step 2: Run verifier; expect FAIL**
`python3 tools/verify_v449_ui.py`
- [ ] **Step 3: Implement patcher/UI**
Add Upcoming above completed recordings. Render channel/title/date/time/status. Selection exposes Edit/Cancel. Manual dialog selects an existing channel and local date/start/end; optional blank title becomes `Manual recording`. Refresh Compose state after add/edit/cancel.
- [ ] **Step 4: Run UI/core verifiers; expect PASS**
`python3 tools/verify_v449_ui.py && python3 tools/verify_v449_schedule_core.py`
- [ ] **Step 5: Commit**
`git commit -am "feat: add upcoming and manual DVR UI"`

### Task 4: Startup recovery, version and regression gate

**Files:**
- Modify: `tools/apply_v449.py`
- Modify: `app/build.gradle.kts`
- Create: `tools/verify_v449.py`

**Interfaces:**
- Consumes all prior 4.49 features.
- Produces Zako 4.49 versionCode 74 release candidate.

- [ ] **Step 1: Write failing aggregate verifier**
Require startup `rearmAll`, 4.49 feature markers, `versionCode = 74`, `versionName = "4.49"`.
- [ ] **Step 2: Run; expect FAIL**
`python3 tools/verify_v449.py`
- [ ] **Step 3: Apply startup recovery/version bump**
Call schedule recovery during safe app initialization without blocking catalog/playback; set 74/4.49.
- [ ] **Step 4: Run full regression suite and compile**
Run all 4.48 verifiers, all 4.49 verifiers, then `gradle assembleDebug --no-daemon --stacktrace`; all must PASS.
- [ ] **Step 5: Commit**
`git commit -am "release: prepare Zako 4.49 managed DVR"`

### Task 5: Signed release and updater

**Files:**
- Create: `.github/workflows/release449.yml`
- Update through workflow: `latest.json`

**Interfaces:**
- Produces signed `Zako-v4.49.apk`, source ZIP, GitHub v4.49 release and updater feed versionCode 74.

- [ ] **Step 1: Copy the proven 4.48 signed-release pattern and add all 4.49 apply/verify steps**
Release verification must require package `com.easyiptv.player`, versionCode 74, versionName 4.49 and certificate SHA-256 `8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165`.
- [ ] **Step 2: Trigger release branch and require GREEN workflow**
No publication claim before completed/success.
- [ ] **Step 3: Verify GitHub release assets and raw updater feed**
Require `Zako-v4.49.apk`, source ZIP, and `latest.json` pointing to v4.49/versionCode 74.
- [ ] **Step 4: Device acceptance**
On Fire TV: create/cancel/edit EPG timer; create manual timer beyond guide; close UI before timer; reboot before another timer; confirm files/status. Runtime acceptance remains distinct from CI.
