# Zako 4.47 Full Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a complete signed Zako 4.47 fresh-install APK with a substantially cleaner TV experience and leaner runtime while retaining proven playback stability.

**Architecture:** Rebuild the full verified 4.46 generated source first, then apply one versioned 4.47 transformation whose behavior is locked by a RED-first verifier. Extract navigation/resource/startup policies into focused Kotlin units and reduce MainActivity responsibility without rewriting the stable Media3 engine.

**Tech Stack:** Kotlin, Jetpack Compose, Android Media3, Gradle 8.7, Java 17, Python release patch/verifier scripts, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-zako-v447-full-redesign-design.md`

## Global Constraints
- package `com.easyiptv.player`
- versionCode `72`; versionName `4.47`
- permanent signing certificate SHA-256 `8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165`
- protect live playback/buffer behavior unless a failing test proves a required change
- low-memory Fire TV (~1 GB) and Android phone support
- full standalone APK; USB optional

---

### Task 1: 4.47 RED contract
**Files:** Create `tools/verify_v447.py`; modify none.
**Produces:** a verifier that fails on regenerated 4.46 and checks the exact 4.47 markers/version/architecture invariants.
- [ ] Write checks for `ZAKO_V447_FULL_REDESIGN`, `TvNavigationPolicy`, `PlaybackResourcePolicy`, `StartupGateState`, compact three-row Mini Guide invariant, persistent update button, v4.44 resume invariant, v4.45 Mini Guide/EPG invariant, version 72/4.47.
- [ ] Run after the full 4.46 regeneration chain and require failure with missing 4.47 marker/version.
- [ ] Commit RED verifier evidence.

### Task 2: Navigation and startup policy extraction
**Files:** Create `app/src/main/java/com/easyiptv/player/TvNavigationPolicy.kt`, `PlaybackResourcePolicy.kt`; modify generated `MainActivity.kt`, `StartupPolicy.kt` via `tools/apply_v447.py`.
**Consumes:** existing focus callbacks and startup-ready state.
**Produces:** deterministic outward/inward D-pad policy and a startup state that blocks navigation until critical content is ready.
- [ ] Add pure-policy RED checks to verifier.
- [ ] Implement enums/data functions with no Android heavyweight dependencies.
- [ ] Wire shell/guide focus callbacks to policy while retaining playback surface.
- [ ] Verify GREEN.

### Task 3: Lean TV shell and visual hierarchy
**Files:** Create `TvShell.kt`; modify generated `MainActivity.kt` via patcher.
**Produces:** top-level destinations and consistent focus styling with reduced root-file responsibility.
- [ ] RED check shell marker/destinations and focus tokens.
- [ ] Move shell-level navigation UI into `TvShell.kt` without moving player lifecycle.
- [ ] Keep pink focused control language and bright-yellow program/search emphasis.
- [ ] Verify GREEN and compile.

### Task 4: Compact Mini Guide and transport behavior
**Files:** Create `LiveOverlay.kt`; modify generated live overlay call sites.
**Consumes:** existing `rowProgram.title`, tune callback, DVR seek callbacks.
**Produces:** three browseable channel rows, EPG/progress, previous-channel and compact transport actions.
- [ ] RED checks for exactly bounded 3-row presentation contract, EPG title and OK tune.
- [ ] Extract overlay composable while preserving current tune/player callbacks.
- [ ] Route media keys to transport policy without requiring scrub-bar focus.
- [ ] Preserve SBS/2D per-channel option.
- [ ] Verify GREEN and compile.

### Task 5: Catalog/search low-memory policy
**Files:** Create `CatalogRuntimePolicy.kt`; modify generated catalog/search call sites and `Data.kt` only where needed.
**Produces:** debounce/local-index search, bounded page/prefetch decisions, live-playback priority.
- [ ] RED checks for bounded page/prefetch constants and live-active throttle behavior.
- [ ] Implement pure policy and connect existing cache/catalog loading to it.
- [ ] Ensure critical startup completes before VOD background expansion.
- [ ] Verify GREEN and compile.

### Task 6: Download/DVR/storage regression hardening
**Files:** Modify `tools/verify_v447.py`; production changes only if a RED invariant exposes a defect.
**Produces:** preservation of Range/206 resume, partial files, USB optionality, stream-limit conflict handling, DVR isolation.
- [ ] Add version-independent checks for 4.44 resumable behavior and existing recording/storage boundaries.
- [ ] Run RED/GREEN cycle for any exposed defect before changing production code.
- [ ] Verify live buffer constants/engine path are unchanged.

### Task 7: Complete release workflow
**Files:** Create `.github/workflows/release447.yml`.
**Produces:** deterministic full regeneration, RED-before-apply, GREEN-after-apply, signed APK/source ZIP and release.
- [ ] Copy the proven full 4.46 regeneration sequence rather than applying to raw v4.23 source.
- [ ] Run `verify_v447.py` before apply and require expected RED.
- [ ] Apply 4.47; run verifier and version-independent legacy verifiers.
- [ ] Build signed release with Java 17/Gradle 8.7.
- [ ] Verify package/version/certificate.
- [ ] Package `Zako-v4.47-source.zip` including app/tools/scripts/docs/build files.
- [ ] Publish `Zako-v4.47.apk`, source ZIP and point `latest.json` to 72/4.47.

### Task 8: Final multi-pass verification
**Files:** no new production files unless a failing gate requires a TDD fix.
**Produces:** evidence-backed release readiness.
- [ ] Review generated-source diff for accidental removal of prior markers/features.
- [ ] Run all 4.47 verifier/build/signature/release checks fresh.
- [ ] Inspect workflow job steps and logs; no red/cancelled/skipped required gates.
- [ ] Confirm published release contains both full APK and source ZIP.
- [ ] Only then report 4.47 ready for fresh installation and Claude review.
