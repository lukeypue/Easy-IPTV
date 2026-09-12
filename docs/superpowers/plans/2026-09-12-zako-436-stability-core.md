# Zako 4.36 Stability Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce random low-memory/process exits without changing Zako 4.35's visible UI or feature set.

**Architecture:** Add a bounded stability layer that records crashes/memory pressure, supervises duplicate background catalog jobs, and persists Movie/Series catalog data to a built-in SQLite index. Integrate it through the existing patch-chain release model so the generated app remains visually identical.

**Tech Stack:** Kotlin, Android SDK, Jetpack Compose, Media3/ExoPlayer, kotlinx.coroutines, SQLiteOpenHelper, GitHub Actions, Python patch/verifier scripts.

**Spec:** `docs/superpowers/specs/2026-09-12-zako-436-stability-core-design.md`

## Global Constraints
- Preserve the Zako 4.35 visual design and user-facing feature set.
- Target Android/Fire TV with roughly 1 GB RAM and D-pad-only navigation.
- Keep one primary Media3 player; no multiview or second permanent player.
- Do not aggressively increase Live startup buffering.
- Keep 4.35 as updater target until 4.36 passes build/sign/release verification.
- Version target: versionCode 61, versionName 4.36.

---

### Task 1: Add RED verifier for 4.36 stability contracts

**Files:**
- Create: `tools/verify_v436.py`

**Interfaces:**
- Consumes: generated source after applying patches through 4.35.
- Produces: deterministic checks for `StabilityCore.kt`, lifecycle integration, supervisor markers, SQLite catalog index markers, and version 61/4.36.

- [ ] **Step 1: Write verifier that checks versionCode 61, versionName 4.36, `ZAKO_V436_STABILITY_CORE`, `ZAKO_V436_CRASH_DIAGNOSTICS`, `ZAKO_V436_RESOURCE_SUPERVISOR`, `ZAKO_V436_CATALOG_SQLITE`, MainActivity lifecycle hooks, and catalog persistence calls.**
- [ ] **Step 2: Run it after the 4.35 patch chain and confirm it fails because 4.36 is absent.**
- [ ] **Step 3: Commit the RED verifier.**

### Task 2: Add StabilityCore.kt and patch integration

**Files:**
- Create through patch: `app/src/main/java/com/easyiptv/player/StabilityCore.kt`
- Create: `tools/apply_v436.py`
- Modify through patch: `app/src/main/java/com/easyiptv/player/MainActivity.kt`
- Modify through patch: `app/src/main/java/com/easyiptv/player/Data.kt`
- Modify through patch: `app/build.gradle.kts`

**Interfaces:**
- Produces: `object StabilityCore` with `install(context)`, `note(event)`, `noteScreen(name)`, `onTrimMemory(level)`, `onLowMemory()`, `beforeBackground()`, and `shutdown()`.
- Produces: `object BackgroundWorkSupervisor` with `replace(tag, job)`, `cancel(tag)`, `cancelNonEssential()`, and `cancelAll()`.
- Produces: `class CatalogDiskIndex(context)` with `replaceCatalog(key, movies, series)`, `loadMovies(key)`, `loadSeries(key)`, and `clear(key)`.

- [ ] **Step 1: Patch versionCode/versionName to 61/4.36.**
- [ ] **Step 2: Generate `StabilityCore.kt` using only Android/Kotlin APIs already available. Diagnostics use a bounded rolling file and delegate to the prior uncaught-exception handler.**
- [ ] **Step 3: Add `MainActivity` lifecycle calls: install in `onCreate`, log screen/app lifecycle, invoke memory-pressure handlers in `onTrimMemory`/`onLowMemory`, and shutdown on destroy.**
- [ ] **Step 4: Integrate the supervisor around the on-demand catalog `LaunchedEffect` so replacement/recomposition cannot leave duplicate catalog jobs alive. Cancel nonessential catalog work on background/low memory; do not cancel recordings/downloads.**
- [ ] **Step 5: Add SQLiteOpenHelper-backed Movie/Series index in `StabilityCore.kt` and call it after successful on-demand loads. Keep `AppData` and UI signatures unchanged.**
- [ ] **Step 6: Run `tools/verify_v436.py` and confirm GREEN.**
- [ ] **Step 7: Commit implementation.**

### Task 3: Add release workflow with RED→GREEN enforcement

**Files:**
- Create: `.github/workflows/release436.yml`

**Interfaces:**
- Consumes: patch chain through `apply_v435.py`, then RED verifier, `apply_v436.py`, GREEN verifier.
- Produces: signed `Zako-v4.36.apk`, generated-source ZIP, GitHub release `v4.36`, and updater metadata for versionCode 61.

- [ ] **Step 1: Copy the known-good 4.35 release workflow structure and append 4.36 patch/verification.**
- [ ] **Step 2: Enforce that `verify_v436.py` fails before `apply_v436.py` and passes after it.**
- [ ] **Step 3: Build with Java 17/Android SDK/Gradle 8.7 and existing permanent signing key.**
- [ ] **Step 4: Verify APK identity/signature, package generated source, publish `Zako 4.36 Stability Core`, update `latest.json` to versionCode 61/versionName 4.36 only after publish, and upload artifact.**
- [ ] **Step 5: Commit workflow and push the isolated 4.36 branch.**

### Task 4: CI verification and updater race check

**Files:** none unless CI exposes a defect.

**Interfaces:**
- Produces: fresh evidence of successful workflow, signed APK, release assets, artifact digest, and correct final updater pointer.

- [ ] **Step 1: Poll the 4.36 workflow to completion.**
- [ ] **Step 2: If a step fails, inspect logs, patch the exact cause, and rerun until green.**
- [ ] **Step 3: Verify release `v4.36` contains `Zako-v4.36.apk` and `Zako-v4.36-source.zip`.**
- [ ] **Step 4: Verify `main/latest.json` points to versionCode 61/versionName 4.36 after all competing old workflows finish.**
- [ ] **Step 5: Only then tell the user to update and begin real Fire Stick soak testing.**
