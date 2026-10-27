# RYZOD 4.81 — Launch fixes

Version 4.81 · Android version code 105 · package `com.easyiptv.player`.

- Smooth (0s) uses direct playback with zero added startup or recovery wait. It skips the mini guide and status polling; essential decoder buffering and stall recovery remain.
- Series navigation drills down from series to seasons to episodes. Back returns from episodes to seasons, then to series, with episodes ordered within their season.
- Android menus keep their headers and top controls visible when opened.
- Stopping a recording immediately cleans up its session and partial output, including a stop before startup finishes.

Install the standard APK on Android phones/Android TV or the FireTV APK on Fire TV. Updates retain the existing package and permanent signing certificate so saved playlists and settings can be kept. RYZOD supplies no channels or subscriptions; use content you are authorized to access.

## Release verification and packaging

The `release481.yml` workflow targets `ryzod-v4.81-launch-fixes` and requires full direct and Fire TV unit suites, at least 200 tests each, with no failures, errors or skips. Both distributions retain lint and minified, signed release APK builds. Before upload, each direct/FireTV/Amazon APK must match version 4.81/code 105, the package, target SDK 36, the permanent certificate SHA-256 `8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165`, APK signature verification, ZIP alignment and native-library alignment gates. R8 mapping files are retained with the builds.

Amazon and Play candidates restore the 4.75 distribution/launcher tests and lint: signed Amazon APK and signed Play AAB, Amazon's no-`REQUEST_INSTALL_PACKAGES` permission gate, and Play's JAR signature/certificate checks. These packages are retained as workflow artifacts only. This workflow does not publish to Amazon Appstore or Google Play, or assert store acceptance.

The publisher checks both updater manifests from one pinned `main` commit before writing. It rejects a newer current version and rejects an equal version with different direct/TV digests or other manifest identity fields. Uploaded public release assets must match the local APK names, sizes, URLs and GitHub SHA-256 digests. Both manifests are promoted in one non-force commit; a concurrent `main` change fails rather than being overwritten. Identical reruns are harmless, and existing release APKs are verified rather than clobbered. The release is marked latest only after the manifest checks and promotion succeed.

The new workflow uses the shared `ryzod-release` concurrency group with cancellation disabled. Future release workflows must adopt that group and the same monotonic publisher guards. **Legacy manual-rerun risk remains:** historical workflows and publishers have version-specific concurrency and may overwrite updater manifests or mark an older release latest. Editing this workflow cannot secure workflow definitions already stored in historical commits. Avoid manually rerunning old release jobs; repository operators must separately restrict or disable legacy release execution. The new group does not serialize those old workflows, and cannot prevent them writing afterward.

The public download page is branded RYZOD and links to GitHub's latest-release page, where users choose the actual published platform asset. It does not assume an unpublished version or a permanent `Zako.apk` asset.

## Validation limits

The packaging change is verified with offline Python publisher tests. Android unit tests, lint and package/signing gates are defined for CI; local Gradle verification is owned by the main implementation task. No real Android/Fire TV hardware or provider playback testing is claimed. Smooth removes deliberate waiting; network delivery and decoder requirements still determine actual startup latency. Hardware, provider and store validation remain separate release responsibilities.
