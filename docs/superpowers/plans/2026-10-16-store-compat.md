# RYZOD store compatibility implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan inline.

**Goal:** Prepare SDK 36 compatibility while preserving tested Fire TV playback, and replace repeated icon guesses with device evidence.

**Architecture:** Keep one application identity and the existing approved artwork. Upgrade only necessary build tools, handle system service limits explicitly, preserve partial recordings/downloads, and inspect launcher resources independently of the proprietary Fire home screen.

**Tech Stack:** Kotlin 2.0.21, Compose, AGP 8.10.1, Gradle 8.11.1, JDK 17, Android API 36.

**Spec:** User requests in this conversation; research in docs/RYZOD-4.75-fire-tv-research.md.

## Global Constraints

- Preserve applicationId com.easyiptv.player and permanent signing key.
- Keep minSdk 26; compileSdk/targetSdk 36; version 4.75 / 99.
- Preserve 4.74 Live TV, search and recording behavior.
- Do not uninstall, erase user data, or claim the Fire icon fixed without affected-device observation.
- No new rolling buffer or speculative logo replacement.

## Review Focus

- Download timeout must keep its partial file and avoid advancing the queue into another exhausted service.
- Recording timeout must close its writer and prevent automatic recovery from immediately restarting it.
- Android 16 back gestures must continue through AndroidX callbacks.
- Phone controls must avoid system bars, keyboard and cutouts; TV player geometry must remain unchanged.
- TV launcher icon, banner and both launcher categories must preserve full artwork at tvdpi through xxxhdpi, including a non-TV configuration used by an external loader.

### Task 1: SDK compatibility

**Files:** build.gradle.kts, app/build.gradle.kts, gradle/wrapper/gradle-wrapper.properties, MainActivity.kt, Recording.kt, Downloads.kt; new compatibility tests.

**Interfaces:** Existing Service.onTimeout(Int,Int), AndroidX BackHandler and Compose safeDrawing insets.

- [ ] Add failing tests for service timeout cancellation/retained files, queue suppression, and recovery removal.
- [ ] Upgrade AGP/Gradle and compile/target SDK; retain Kotlin and playback dependencies.
- [ ] Add bounded timeout cleanup, phone safeDrawing padding and AndroidX back delegation.
- [ ] Run focused tests then full testDebugUnitTest/lintDebug/assembleRelease/bundleRelease.

### Task 2: Fire TV evidence

**Files:** LauncherActivityIconTest.kt, new LauncherDiagnostics.kt, MainActivity.kt Settings, research document.

**Interfaces:** PackageManager application/activity/ResolveInfo icon/banner loading; read-only diagnostics.

- [ ] Test app/activity/resolved icons and banners across API 26/28/30, tvdpi/hdpi/xhdpi/xxxhdpi and phone/default configuration.
- [ ] Add device, OS, density, launcher package and resolved-artwork report with checksums and resource paths. Display only user-relevant device/artwork status in Settings.
- [ ] Preserve exact existing artwork while documenting proven sources versus unproven cache hypothesis.
- [ ] Run full suite and optimized packaging; inspect resource table and native alignment.

### Task 3: Review and release preparation

**Files:** RELEASE_4.75.md, versioned workflow/publisher, docs/RYZOD-4.75-validation.md.

- [ ] Update release workflow to Gradle 8.11.1, full checks and APK/AAB artifacts.
- [ ] Independent whole-branch review, address material findings, rerun affected/full checks.
- [ ] Prepare signed testing update using existing permanent-key CI; verify version, signature and download metadata.
- [ ] Record device/store submission limits accurately; do not call automated tests real Fire launcher validation.

## Execution record

- Native execution on dedicated branch ryzod-v4.75-store-compat, based on the released 4.74 source tree. Existing baseline was 121 green tests.
- Timeout tests initially reproduced missing handlers. Independent review then identified background polling, pre-entry cancellation and notification cleanup defects. Regression tests reproduced RUNNING instead of FAILED before fixes. Shared BackgroundTransferBudget now blocks every queue dispatch until Activity.onStart; timeout eagerly publishes resumable terminal state and removes its notification.
- Ruling: supply dedicated Fire and Amazon distributions with unconditional wide default artwork, preserving phone/default artwork. This addresses external loaders ignoring television qualifiers; it cannot guarantee removal of Amazon's cached tile.
- Ruling: property-selected distributions retain existing task names/application ID and signing identity; store overlays remove APK installation permission. Costs if incorrect: store packaging rejected by release checks; existing installed app data remains intact.
- Local network/cache workaround: Gradle 8.11 artifact revalidation received HTTP 403 for already cached test libraries. An untracked temporary local Maven mirror exposes cached original artifacts; CI continues using standard repositories.

- Ruling: upgrade Robolectric to 4.16.1 because its official compatibility table supports API 36, unlike 4.14.1. Add API 35/36 timeout coverage and API 36 live-guide UI tests. Existing menu tests developed idle failures during the complete migration run; interrupted that run for diagnosis rather than reporting it as passed.

- New user report: audio during app startup and historical recording audio over YouTube. Reproduced Activity.onStart reviving last playback and onPause leaving audio enabled until onStop. Move resumption to ready/visible video plus RESUMED lifecycle; pause at onPause. Preserve explicit playback intent and selected live index while deferred. Added multi-channel and saved-progress prompt tests; both reviewer findings demonstrated failures before fixes.
- Timeout retirement race: a fresh dispatch into the old timed-out service left RUNNING without a writer. Matching id/generation reservation now becomes resumable and is released; regression reproduced the leak first.
- Test harness: full mixed graphics runs intermittently hit Compose idle loops (CatalogRefreshUiTest and LibraryItemMenusTest); use consistent native Android graphics for higher fidelity, and run unit tests separately from lint with one worker. No assertions are skipped or relaxed.

- Final harness isolation: native graphics alone did not eliminate shared-process idle failures (GuideInteractionTest also reproduced one). Fork a fresh JVM per test class to isolate process singletons/native state; do not claim a proven production layout defect or proven graphics-mode root cause from these harness symptoms. Final results must be read from the complete isolated run.

- Final local checks: full isolated suite 147/147, no skips; zero lint errors/75 warnings; optimized unsigned release passed, ZIP alignment and all 8 ELF libraries verified at 16 KB. Dedicated Fire packaging checks pass. The remote-menu fixture now establishes keyboard mode and asserts focus before dispatch; no production navigation workaround or relaxed assertion was used. Signing/publication remains enforced by release475 workflow.
