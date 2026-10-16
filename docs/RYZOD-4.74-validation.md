# RYZOD 4.74 validation

Version 4.74, version code 98. Android/Fire TV source based on the 4.73 movie-artwork branch. Checked on October 16, 2026 (UTC).

## Result

The complete Android automated suite passes: **121 tests, no failures, errors, or skips**. Android lint completes with **zero errors and 34 warnings**. The optimized release build succeeds. A focused independent code review found pause/recovery edge cases, which were corrected and included in the final checks.

| Check | Result |
| --- | --- |
| Full `testDebugUnitTest` suite | 121 passed |
| `lintDebug` | 0 errors; 34 warnings |
| Optimized `assembleRelease` | Passed; local unsigned build |
| Existing release contracts | 3 passed |
| New Live TV/search contracts | 11 passed; relevant checks first demonstrated failures |
| Packaged application | `com.easyiptv.player`, version 4.74 / 98 |
| APK ZIP alignment | Passed with 16 KB native-library page alignment |
| Native ELF load segments | All 8 packaged libraries have at least 16 KB alignment |
| Current workflow YAML | 55 active workflows parse; malformed historical 4.67/4.68 workflows preserved with `.disabled` suffix |
| Source whitespace check | Passed |

Local unsigned APK SHA-256: `fd444c856b29430dd922b7be68c6928e9de8f07f82b793dc03e905eef4d40fb8`.

The permanent-key signed APK, GitHub release, and updater-manifest publication require the release workflow. They have not run for 4.74. The local APK is not an upgrade artifact for the installed production app.

## Changes checked

- Live OK/Enter opens the mini guide before PlayerView sees the key. Repeats and the matching release cannot activate its newly focused Pause control; ordinary channel-key repeat remains available.
- One direct Live TV mode replaces the obsolete Smooth/DVR mode switch. Pause and explicit recording requests start temporary storage; normal tuning does not start a rolling disk buffer. Clear LIVE and pause-history labels replace DVR RETRY.
- Initial pause reading requires real written TS data. Storage failure and automatic retries preserve intentional pause, including HLS live-window recovery. A saved recording with late-arriving initial data can still attach its reader.
- Resumed temporary playback uses bounded reader recovery and protects an active saved recording's writer. An intentional pause is excluded from buffering recovery. LIVE resumes paused playback without replacing the recording writer.
- Background/foreground transitions retain the user's playback request. The existing rule that hidden non-recording Live TV stops and clears temporary history remains in force.
- Search work stays off the UI thread, briefly coalesces edits, and reuses prepared word boundaries. Down past the keyboard's bottom row moves into results while the keyboard stays open. Up from the first result group returns to it. Top and side arrows stay within the keyboard.
- The approved square source artwork matches the user's uploaded image pixel for pixel. Fresh manifest resources select square phone and wide TV artwork; legacy plain-R fallback resources now point to the complete approved logo. Packaged release resources include the fresh icon/banner identities.
- Stop Recording requires confirmation in the mini guide and phone player controls. Play/Pause labels reflect the requested state during buffering.

## Reproduction

Run `python3 tools/verify_v470.py`, `python3 tools/verify_v474.py`, then `gradle testDebugUnitTest lintDebug assembleRelease --no-daemon` with JDK 17, Gradle 8.7, and Android SDK 35.

This workspace required a temporary Gradle init script to put build outputs outside the synchronized source directory: its transient `.rsync-tmp` files interrupted Gradle's APK output tracking. That workaround changes output location only and is not part of the app or CI configuration. A temporary network-proxy init script allowed Robolectric's SDK downloads. The final complete build succeeded with normal tracked packaging in the isolated output directory.

## Before public launch

Physical Fire Stick/phone/provider playback has not been exercised here. Automated tests use controlled providers and Robolectric; they cannot establish decoder, Wi-Fi, USB, launcher-cache, or long-duration behavior on the actual devices.

1. Install the permanent-key signed update over the existing app and verify Settings shows 4.74. Check the full RYZOD name on the TV tile and the phone icon; restart the launcher/device if its previous rendered image remains cached.
2. Watch Live TV, repeatedly open/close the mini guide with OK, and verify the picture keeps playing. Pause for several minutes, resume, and watch long enough to check for repeated buffering. Test LIVE while a recording is active.
3. Record the watched channel with a one-stream provider setting. Check scheduled recording, stop/cancel confirmation, saved playback and delete confirmation; repeat using the intended USB destination.
4. Type several searches, including `20/20`, and test bottom exit/up return plus side/top wrapping. Check movie/series artwork and categories.
5. Verify download pause/resume and queue advance across a network loss, saved-video playback, channel changes, and background return. Verify a paused show stays paused after recovery/foreground return.

The current phone build targets API 34. Google's current new-phone-app/update requirement is API 36, so a Google Play phone submission needs a target/compile SDK migration and its behavior validation first. Android TV has a separate API 34 minimum. Official source: [Google Play target SDK requirements](https://developer.android.com/google/play/requirements/target-sdk). This stability update does not by itself establish store-submission readiness.

The lint warnings cover unused resources, dependency/version notices, SDK/API notices, package-query declarations, preferences, drawable placement, and lifecycle-held view references. No lint errors remain; warnings are retained in the lint report rather than represented as a clean-warning result.

## Automated suite inventory

| Test suite | Cases | Failures |
| --- | ---: | ---: |
| CatalogRefreshUiTest | 2 | 0 |
| CatalogRepairTest | 9 | 0 |
| DiagnosticLoggingTest | 1 | 0 |
| DownloadQueueOwnershipTest | 7 | 0 |
| DvrOwnershipRegressionTest | 13 | 0 |
| EpgCancellationTest | 5 | 0 |
| GuideGeometryTest | 4 | 0 |
| GuideInteractionTest | 3 | 0 |
| InputSearchTest | 1 | 0 |
| LauncherActivityIconTest | 6 | 0 |
| LauncherArtworkTest | 2 | 0 |
| LibraryItemMenusTest | 9 | 0 |
| LiveControlsRegressionTest | 6 | 0 |
| LivePlaybackHealthTest | 10 | 0 |
| LiveTvUiRegressionTest | 2 | 0 |
| MediaFormatSupportTest | 1 | 0 |
| MovieArtworkLookupTest | 6 | 0 |
| MoviePosterUiTest | 3 | 0 |
| PlaybackMemoryPolicyTest | 5 | 0 |
| RecordingNetworkTest | 3 | 0 |
| RecordingOwnershipTest | 1 | 0 |
| RecordingRecoveryTest | 6 | 0 |
| SearchIndexTest | 4 | 0 |
| SearchKeyboardTest | 4 | 0 |
| TimeshiftPerformanceTest | 3 | 0 |
| TimeshiftRingRegressionTest | 3 | 0 |
| TimeshiftSessionTest | 2 | 0 |
