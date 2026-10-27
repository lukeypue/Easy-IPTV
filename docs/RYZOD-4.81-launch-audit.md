# RYZOD 4.81 prelaunch audit

Baseline: full 4.80 source, commit 165e5fdaa5c5b3bb7b7e1abaae4a2c0df26fd4d2. The default main branch is older (4.54); it must not be used as the app source. main currently also hosts updater manifests. This audit uses available conversation retrieval, supplied history, repository release notes and source. It does not establish access to every historical conversation.

## Requirement ledger

| Area | Preserved requirement and verification boundary |
| --- | --- |
| Brand | RYZOD; preserve application ID and permanent signer for upgrades. Old Zako names in internal paths do not require destructive migration. Correct public download page branding/link. Fire OS launcher caching/aspect still requires both affected physical Sticks. |
| Live | One shared ExoPlayer, provider transport unchanged, no rolling disk buffer. History starts only on Pause, bounded at 30 minutes and cleared on channel changes. Keep one-stream recording conflict messages and ownership. |
| Smooth | New opt-in direct mode, zero added startup/recovery waits, no mini guide, no buffering message/percentage polling or program-info fetch in full-screen live. Small decoder queue remains necessary. Pause-only history and essential stall recovery remain. OK/Left returns to full guide; Back remains available. |
| Buffer choices | Retain 10/20/30/60 refill and 0/3/4/6/10/20/30 startup settings, Natural/Steady at 1x; choosing manual buffering leaves Smooth. Physical provider starvation cannot be eliminated by a setting. |
| Guide | Duration-based blocks, clickable blanks, current channel return, compact rows, green category heading, upper-left preview, upper-right mark, centered update action/provider explanation. Drawer opens with Main menu/Favorites visible for All channels. |
| Series | Series > seasons > episodes. Back climbs one level. Numeric queue ordering, resume/watched state, Play/Download/Close retained. State belongs to source + series. |
| Movies/artwork | Refresh controls, lazy bounded artwork/fallback work retained. Provider artwork and high-count scroll verification requires an actual catalog. |
| Search/keyboard | First-letter search, cancellation, symbols, remote wrap, bottom/Done exits, focus into results retained. Keyboard accessibility/latency on both Sticks requires device confirmation. |
| Downloads | Queue, interrupted transfer resume, pause, delete confirmation, playback and explicit stop retained. Wi-Fi and USB removal require device tests. |
| Recording | Manual schedule up to one week and 30–300 minute choices, blank EPG support, confirmation, persistent retry/recovery. Fix immediate Stop lifecycle race with owner-safe completion cleanup. Reboot/alarm, timezone and USB behavior need hardware verification. |
| Updates | Same signer/package, monotonic versionCode, distribution-specific APK/hashes and manifest validation. Signed release gate builds direct/Fire; restore Amazon APK/Play AAB gates. No debug replacement of customer app. |
| Platforms | Android/Fire first. Existing Apple/Roku branches require separate parity work and hardware tests; this update does not prove parity or complete App Store/Roku publication. Apple signing/TestFlight and Roku companion requirements remain. |
| Launch/business | Seven-day trial and proposed direct $5.99–$6.99 / stores $7.99 were historical plans, not evidence of implemented billing/licensing. No billing/trial/licensing integration found by source search. Verify commercial setup, legal/support content, store listings and developer accounts separately before paid launch. |

## Independent review

A separate AI reviewer checked recording/download lifecycle, updater, signing and store packaging. It found early-stop RecordingService cleanup risk, the obsolete public APK URL, and older-release rerun rollback risk. The new promotion path must reject downgraded/conflicting manifests. Historical workflows run from older commits remain an administrative risk and should not be rerun to promote old versions.

## Physical acceptance before launch

Test signed upgrade over current app preserving playlists, favorites and settings; Android touch + Fire remote menu entry; all seasons and Back navigation; Smooth channel changes and long session; pause/resume and single-stream recording; interrupted downloads/USB; reboot/scheduled-recording recovery; background/foreground audio; both affected Fire home artwork paths. Automated tests are evidence of covered behavior, not proof that every provider/device is bug-free.

## Earlier decisions retained or superseded

Available August history adds offline access to Downloads/Recordings, channel wrap, readable remote focus and guide times, previous-channel history, live CC and picture size, accelerated FF/REW, per-type frame-rate matching, configurable download retention, and direct USB destinations. The early mandatory one-provider-connection constraint evolved into the explicit 1/2/3-stream budget, defaulting to one. The 45-minute rolling timeshift request was superseded by the user's later pause-only 30-minute requirement. Old virtual future Content-Length/range emulation must not return. These points are covered by existing source/test contracts and still require physical storage/provider checks.

## Current verification record

- Existing guide/live structural contract checks: 14 tests passed; scheduler, recording ownership and recovery checks passed.
- First focused UI/policy/cleanup run: 40 tests passed; Android 16 required Java 21, subsequently installed.
- First full Android run: 208 tests, two recording cleanup regressions found (delayed active-state clearing). Corrected cleanup to finish ownership/state synchronously and gate deferred service callbacks by coroutine identity.
- Corrected lifecycle + live/player targeted rerun: 40 tests passed, zero failures, including Android 16.
- Publisher: 13 offline tests passed. Independent code review found no blocking issue before that final lifecycle correction.
- The release workflow must independently pass full direct and Fire TV suites, lint, minified signed builds, certificate/hash/native alignment validation, and Amazon/Play candidate checks before release promotion. Do not infer hardware verification from these gates.
