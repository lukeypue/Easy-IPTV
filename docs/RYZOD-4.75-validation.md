# RYZOD 4.75 validation

The complete local suite passes: **147 tests, no failures, errors or skips**. Android lint completes with **zero errors and 75 warnings**. The optimized universal release APK builds successfully. This report records local checks before publication; the release workflow repeats the complete suite for universal/Fire distributions, checks the store variants, and verifies signed artifacts before publishing.

## Scope

Based on released 4.74 source, preserving `com.easyiptv.player`, minSdk 26 and the permanent release signing identity. Compile/target SDK 36, AGP 8.10.1, Gradle 8.11.1 with pinned distribution checksum, Kotlin/Compose compiler 2.0.21. JDK 21 runs the Android 16-capable Robolectric 4.16.1 tests; app Java/Kotlin bytecode remains target 17. Playback/FFmpeg versions are unchanged.

## Behaviors covered

- Live OK/search/temporary pause/recording regressions from 4.74 remain in the complete suite.
- Data-sync timeouts stop services, retain received recording video and partial download bytes, remove ongoing notifications and suppress all queue dispatch until foreground return. Cancellation before coroutine entry and a fresh dispatch into a retiring service have explicit regressions.
- Activity start no longer revives the last channel behind loading/home. onPause silences playback before onStop. Playback resumes only for a ready, visible full-screen/corner player in RESUMED lifecycle. Background recording and recording during app handoff do not revive hidden playback.
- Deferred playback retains the selected live channel index. Saved-progress prompts retain intentional pause until Resume/Start over.
- Phone safe drawing insets handle system UI/cutouts/keyboard. Existing AndroidX back handlers are exercised on API 36 as well as API 28.
- Fire/Amazon APKs override default and v26 artwork to wide form, including resource loads with a non-television configuration. Phone/direct and Play retain square default icons; TV banner remains wide. API 26/28/30 and density requests 160/213/240/320/480/640 are checked.
- Settings Device & artwork check displays installed icon/banner plus actual package version/update time, device model, Android build, density/UI mode and home launcher version. Resource paths and bitmap checksums are read-only evidence, not control of Amazon's home-screen database.

## Distribution commands

Use JDK 21, SDK platform 36 and Gradle 8.11.1:

- `gradle testDebugUnitTest --max-workers=1`, then `gradle lintDebug assembleRelease`: universal/phone APK with self updates.
- Add `-PryzodDistribution=fire`: dedicated wide-artwork APK with self updates.
- Add `-PryzodDistribution=amazon`: wide-artwork APK, store-managed updates, no REQUEST_INSTALL_PACKAGES.
- Add `-PryzodDistribution=play`: phone/default artwork, store-managed updates, no REQUEST_INSTALL_PACKAGES; `bundleRelease` produces AAB.

Unit tests use consistent native graphics and a fresh JVM per test class, isolating process-owned playback/recording state and native resources. Earlier shared-process full runs intermittently produced Espresso idle failures in existing catalog, guide and menu tests; those failures were not consistently reproduced in focused runs. Tests run separately from lint with one worker; assertions are not skipped or relaxed. Older-framework and pre-fix failures are retained in the investigation record rather than represented as successful verification.

This workspace uses temporary init scripts for isolated build output and access to cached Maven artifacts after HTTP 403 revalidation failures. These environment workarounds are not committed build dependencies. CI uses the normal repositories.

## Device/store limits

Robolectric runs Android framework code in a JVM. Tests do not establish actual Fire launcher cache behavior, hardware audio/video decoding, Wi-Fi reliability, six-hour OS enforcement, or all phone insets/back gestures. Both the user's 16 GB and cousin's 8 GB sticks still require affected-device testing.

On Fire TV install the FireTV APK over the existing signed app. Older 4.74 updaters choose the universal asset; use the dedicated FireTV download once to test the unconditional wide path. Check Settings reports distribution `fire`, then compare installed artwork preview against small pinned tile and full Apps tile. Do not uninstall or erase playlists to troubleshoot the icon.

Test cold launch and return from YouTube while the app refreshes its lineup; there must be no last-channel audio behind startup/home. Start a scheduled recording while YouTube is active, then confirm saved video exists and RYZOD stays silent. Return to a visible paused show and verify it remains paused. Select a channel other than the first; check correct tuning after loading. Check Resume/Start over waits for a choice.

Amazon/Play outputs are submission candidates, not store acceptance. Developer accounts, listing graphics (Amazon requires exact opaque 1280 × 720 PNG), screenshots, privacy/data declarations and remaining product policies need review in their respective consoles. No store listing has been submitted here. See [Fire TV research](RYZOD-4.75-fire-tv-research.md) for primary sources and the distinction between confirmed facts and hypotheses.

## Local result summary

| Check | Result |
| --- | --- |
| Complete unit/UI suite | 147 passed; no failures/errors/skips |
| Fire distribution launcher/update/permission checks | 17 passed; no failures/errors/skips |
| Lint debug | 0 errors; 75 warnings |
| Optimized universal assembleRelease | Passed; local unsigned APK |
| Packaged identity | com.easyiptv.player, version 4.75 / 99, min SDK 26, target/compile 36 |
| APK ZIP native alignment | 16 KB check passed |
| Native ELF alignment | All 8 libraries: every load segment at least 16 KB |
| Existing source contracts | 3 + 11 passed |
| Workflow YAML/Python parsing and source whitespace | Passed |

Local unsigned APK SHA-256: `5845cdd6125cf67b83a743a42d9c49f2b01e499a6540afa34e7dbc4c618d530c`. This is not the permanent-key signed upgrade artifact.

Lint warnings are retained, primarily KTX style suggestions, unused resources, preferences, SDK notices, receiver handling and lifecycle-held references. No warning-free claim is made. Independent review findings on timeout ownership, notifications, audio lifecycle, deferred channel selection and resume prompts were addressed with regression tests.

The remote-menu test fixture now explicitly requests keyboard mode before focus, matching the guide tests; the new pre-key assertion demonstrated touch-mode focus was not granted. Focus and non-autoplay assertions remain enabled.

## Complete suite inventory

| Suite | Cases | Failures |
| --- | ---: | ---: |
| CatalogRefreshUiTest | 2 | 0 |
| CatalogRepairTest | 9 | 0 |
| DiagnosticLoggingTest | 1 | 0 |
| DistributionTest | 1 | 0 |
| DownloadQueueOwnershipTest | 9 | 0 |
| DvrOwnershipRegressionTest | 14 | 0 |
| EpgCancellationTest | 5 | 0 |
| GuideGeometryTest | 4 | 0 |
| GuideInteractionTest | 3 | 0 |
| InputSearchTest | 1 | 0 |
| LauncherActivityIconTest | 9 | 0 |
| LauncherArtworkTest | 2 | 0 |
| LauncherDiagnosticsTest | 2 | 0 |
| LibraryItemMenusTest | 9 | 0 |
| LiveControlsRegressionTest | 8 | 0 |
| LivePlaybackHealthTest | 10 | 0 |
| LiveTvUiRegressionTest | 10 | 0 |
| MediaFormatSupportTest | 1 | 0 |
| MovieArtworkLookupTest | 6 | 0 |
| MoviePosterUiTest | 3 | 0 |
| PlaybackMemoryPolicyTest | 5 | 0 |
| RecordingNetworkTest | 3 | 0 |
| RecordingOwnershipTest | 1 | 0 |
| RecordingRecoveryTest | 10 | 0 |
| SearchIndexTest | 4 | 0 |
| SearchKeyboardTest | 4 | 0 |
| TimeshiftPerformanceTest | 3 | 0 |
| TimeshiftRingRegressionTest | 3 | 0 |
| TimeshiftSessionTest | 2 | 0 |
| UpdateDistributionTest | 3 | 0 |
