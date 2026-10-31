# RYZOD 4.82 — Search and guide

Version 4.82 · Android version code 106 · package `com.easyiptv.player`.

- The search keyboard uses 60% of TV width and usable phone width, with an orange panel, dark keys, cream letters and visible focus. Remote confirmation requires a fresh matching press and release; moving focus cancels pending activation.
- Search retains the last query, vertical results scroll position and bounded results for the active playlist when returning from playback or series navigation.
- The guide keeps a private disk snapshot isolated by guide source. The last valid schedule stays visible while refreshing, after a failed refresh and across process restarts. Freshness is rechecked on resume and at expiry while browsing; automatic guide work stops during playback.
- Smooth has its own on/off switch and an explanation beneath it. Enabling Smooth overrides playback buffering behavior while preserving the user's manual buffer, startup and recovery choices for when Smooth is off. It cannot increase server or network throughput.

Install `RYZOD-v4.82.apk` on Android phones/Android TV or `RYZOD-v4.82-FireTV.apk` on Fire TV. The package and permanent signing identity remain unchanged so existing playlists and settings can be retained. RYZOD supplies no channels or subscriptions; use content you are authorized to access.

## Release gates and parallel packaging

`.github/workflows/release482.yml` runs only on `ryzod-v4.82-search-guide`, including manual dispatch. Direct and Fire builds run in separate matrix jobs alongside a store-candidates job and an offline publisher/contract test job. The publisher requires all these jobs to succeed, including both matrix entries. There is no redundant initial Android test subset.

Each direct/Fire job runs the entire unfiltered phone/universal or Fire TV regression suite. `verify_unit_results.py` requires at least **231 tests per suite**, accepting a higher count, and rejects any failures, errors or skips. Each job also runs lint and a minified signed release build, retaining its nonempty R8 mapping and test reports. The permanent key is restored independently in every build job from the existing `ZAKO_*` secrets, with private file permissions, and removed afterward. Key material and passwords are never printed.

Before artifact upload, direct, Fire and Amazon APKs must pass package/version checks for `com.easyiptv.player`, 4.82/code 106, target SDK 36, APK signature verification, the permanent certificate SHA-256 `8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165`, ZIP alignment and every native ELF library's 16 KB load alignment. The shared verifier rejects missing or additional APK signers.

Amazon and Play retain distribution and launcher tests, lint, minification and signing. A temporary Gradle init script includes only `**/*DistributionTest.class` and `**/*Launcher*Test.class` for these store test invocations, limiting discovery before Robolectric forks. These includes are never applied to the full direct/Fire suites. Store reports require at least nine tests and no failures, errors or skips. The Amazon APK cannot request `REQUEST_INSTALL_PACKAGES`; the Play AAB must pass JAR signature verification and match the permanent certificate. Store candidates and mappings are workflow artifacts only. This workflow never uploads to Amazon Appstore or Google Play and does not assert store acceptance.

The publisher downloads only verified build and store artifacts from the current run. It repeats the package/signature/alignment and store checks, requires all four nonempty mappings, checks checkout HEAD against the workflow SHA, and compares the source ZIP byte-for-byte with `git archive --format=zip HEAD`. The source archive includes exactly the tracked source at that commit.

Only the two public APKs and `RYZOD-v4.82-source.zip` are attached to the GitHub release. Store candidates, mappings and test reports stay in workflow artifacts. A pre-existing tag must resolve to the workflow commit. Existing releases are immutable: reruns verify that exactly the three expected assets have matching names, sizes, URLs and GitHub SHA-256 digests, including the source ZIP; they never overwrite conflicting assets.

## Updater promotion

`tools/publish_v482.py` preserves the 4.81 publisher's monotonic, atomic promotion. Both `latest.json` and `latest-v2.json` are read from one pinned `main` commit. A newer version blocks a downgrade; an equal version with conflicting identity, URLs or APK digests blocks promotion. After verifying the public stable release, both manifests are written in one commit with a non-force ref update. A concurrent main advance fails without a force update or blind retry. Identical reruns are harmless. The release is marked latest only after successful promotion.

The shared `ryzod-release` concurrency group has cancellation disabled. Future workflows must retain it and these guards. Historical workflows still have separate groups and older publisher logic: their manual reruns can bypass these protections, overwrite manifests or mark an older release latest. Repository operators must separately restrict or disable legacy release execution; changing this workflow cannot secure definitions already stored in old commits.

## Validation limits

Offline validation runs all publisher mock regressions, including the copied 4.81 suite and new 4.82 tests for signing, identity, alignment command failures, source identity, immutable release assets and atomic promotion. YAML structure and embedded shell syntax are checked separately.

Local Android regression tests and TV/phone keyboard screenshots were checked during implementation. The release gates run the complete Android and Fire TV suites, lint, and signed-package verification in CI against the completed source commit. Android/Fire TV hardware, provider playback, focus behavior on physical remotes and store acceptance require separate validation. These release notes describe the intended 4.82 behavior, not a claim of completed device checks.
