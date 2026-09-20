# Zako 4.48 DVR and Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver reliable scheduled recording and continuous X1-style rolling live DVR without regressing Zako's stable playback and media features.

**Architecture:** Harden scheduling behind a capability-aware boundary, keep the segmented TimeshiftRing as the disk-first DVR core, and separate DVR timeline/transport semantics from MainActivity UI wiring. Preserve Smooth direct playback as fallback.

**Tech Stack:** Kotlin, Android/Fire OS, Jetpack Compose, Media3, OkHttp, AlarmManager, foreground services, GitHub Actions.

**Spec:** docs/superpowers/specs/2026-09-19-zako-v448-dvr-stability-design.md

## Global Constraints
- Package remains com.easyiptv.player and permanent signing identity must not change.
- Fire TV ~1 GB class remains a primary target.
- One provider connection for watched live + same-channel timeshift/recording.
- No backend/cloud DVR.
- Smooth/direct playback remains fallback.
- Production changes follow red-green tests/checks before implementation.

## Review Focus
- Android exact-alarm access denied: scheduling returns actionable state and never crashes.
- EPG program boundary while staying on one channel: retained DVR history is not reset.
- One-stream account conflict: live/record/download ownership is deterministic and explained.
- USB removal/full storage: no crash; safe fallback occurs.
- Repeated remote FF/REW: speed cycle and LIVE-edge behavior remain deterministic.

---

### Task 1: Reproduce and harden future recording scheduling
**Files:** Create RecordingScheduler.kt; modify Recording.kt, AndroidManifest.xml; add verifier/tests.
**Interfaces:** RecordingScheduler.schedule(context, schedule): ScheduleResult; cancel(...); canScheduleExact(...).
- [ ] Add a failing verifier/test proving the current direct setAlarmClock path can throw when exact-alarm capability is denied.
- [ ] Run RED and capture failure.
- [ ] Add capability-aware scheduler and required manifest declaration/settings intent; convert thrown platform failures into ScheduleResult.
- [ ] Route ScheduleStore through scheduler.
- [ ] Run focused GREEN tests and compile.
- [ ] Commit.

### Task 2: Prove rolling history is independent of EPG shows
**Files:** TimeshiftRing.kt, LiveDvrController.kt, MainActivity.kt, tests/verifier.
**Interfaces:** LiveDvrController.bounds(): DvrBounds; seekTo(...); onChannelChanged(...).
- [ ] Add failing tests that cross a synthetic EPG boundary while ring/channel identity stays unchanged and assert oldest history remains.
- [ ] Run RED.
- [ ] Implement controller whose timeline derives from ring/session timestamps rather than program start/end.
- [ ] Wire live timeline to controller.
- [ ] Run GREEN plus existing ring tests.
- [ ] Commit.

### Task 3: Harden recording ownership and stream budgets
**Files:** Recording.kt, MediaRuntimePolicy.kt/ProviderStreams owner, tests.
**Interfaces:** RecordingDecision for tee/direct/conflict; same-channel ring reader/cursor.
- [ ] Add failing tests for 1/2/3 stream matrices, same-channel tee, different-channel recording, active download yielding.
- [ ] Run RED.
- [ ] Centralize ownership decision and remove duplicated ad-hoc slot arithmetic.
- [ ] Ensure same-channel recording consumes ring without extra provider socket; preserve independent scheduled recording when budget permits.
- [ ] Run GREEN.
- [ ] Commit.

### Task 4: X1-style live transport semantics
**Files:** LiveDvrController.kt, LiveOverlay.kt, MainActivity.kt, tests.
**Interfaces:** transport(action): TransportState with rewind/forward/pause/play/live and rate cycle 2/4/8/16/1.
- [ ] Add failing state-machine tests for repeated FF/REW, pause, clamp-oldest, and jump-live.
- [ ] Run RED.
- [ ] Implement transport state machine independent of Compose.
- [ ] Map D-pad/media keys and compact overlay actions to it.
- [ ] Run GREEN and compile.
- [ ] Commit.

### Task 5: Mini Guide/timeline and settings cleanup
**Files:** LiveOverlay.kt, MainActivity.kt/settings components, tests/verifier.
- [ ] Add failing structural checks for three-row mini guide, current-program text, LIVE-edge timeline copy, and absence of cloud/obsolete single-file DVR copy.
- [ ] Run RED.
- [ ] Wire presentation to retained-history bounds; preserve pink focus/yellow program emphasis and deterministic LEFT hierarchy.
- [ ] Rename storage copy to Local DVR / Extended DVR Storage.
- [ ] Run GREEN.
- [ ] Commit.

### Task 6: Failure and low-memory hardening
**Files:** LiveStorageManager.kt, TimeshiftRing.kt, PlaybackResourcePolicy.kt, diagnostics/tests.
- [ ] Add failing tests/checks for USB disappearance, unsafe free space, reader at reclaimed history, provider stall, and bounded buffers.
- [ ] Run RED.
- [ ] Implement only defects exposed by those tests; fall back internal then Smooth as specified.
- [ ] Run GREEN and compile.
- [ ] Commit.

### Task 7: Full regression audit
**Files:** verification scripts/workflow and only production files implicated by failures.
- [ ] Run generated-source verification for startup gate, Live/Guide, Movies, Series, Search, downloads/resume, recordings, captions, SBS, updater, navigation, and provider budgets.
- [ ] For each failure, reproduce with one focused failing test before changing production code.
- [ ] Run full unit/verifier/build suite to zero failures.
- [ ] Inspect generated MainActivity/runtime references rather than raw pre-generation source.
- [ ] Commit regression fixes separately.

### Task 8: Build and release 4.48
**Files:** build/version scripts, release workflow.
- [ ] Bump versionCode once and versionName to 4.48 only after regression GREEN.
- [ ] Build signed standalone release from complete generated source.
- [ ] Verify package com.easyiptv.player, version 4.48, permanent certificate fingerprint, APK size/hash.
- [ ] Publish APK and source ZIP, verify release assets and updater target.
- [ ] Independently download the artifact and compare hash.
- [ ] Report CI verification separately from device acceptance.
