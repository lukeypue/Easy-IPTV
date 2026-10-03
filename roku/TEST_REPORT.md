# RYZOD Roku 0.1.0 — verification report

Baseline: Android 4.70/build 94, `637c449f79db1f2d784d0561041a06be750c779b`. Branch: `feature/ryzod-roku`. Verified on 2026-10-03 using Node 24, Python 3.12.14, FFmpeg/ffprobe 6.1.1, BrighterScript 0.73.5, @rokucommunity/brs 0.47.6 and brs-node 2.6.0.

## Automated verification

`npm run verify` passes: **35 Roku checks and 16 companion checks**, with no skipped recording test, plus full BrightScript/SceneGraph compilation and package validation. The full suite is repeated after review fixes. ZIPs are built twice and SHA-256 hashes compared for reproducibility.

| Coverage | Evidence |
| --- | --- |
| Production provider/guide/state code | 18 BrightScript behavior checks: encoded UTF-8 credentials, normalized hosts, wrapped catalogs, malformed data, M3U metadata/types, numeric episode order, duration clipping/gaps, original recording bounds, base64 titles, XMLTV offsets, playlist/media key isolation, resume limits, seven-day schedules/DST, keyboard/action wrap, slash searches, stale responses and native seek boundaries. |
| Native SceneGraph runtime | 13 simulator checks: startup/login, three-page keyboard, horizontal actions, guide position/gaps/paging, schedule arrows, empty category control, live search, playlist task/Previous isolation, late episode responses, reservation refusal/expiry/delayed approval, same-item replay, caption identifier/enablement, paged track actions, fullscreen protection and last-playlist removal. |
| Threaded provider Task worker | Real local HTTP fixture exercises authentication, encoded credentials, wrapped catalog, categories, EPG, HTTP failure envelopes and authenticated POST playback reservation. |
| Package contracts | Three checks for all eight menus, one Video, remote handlers, async requests/cancellation. Build script additionally parses every component XML, resolves packaged script/image paths, validates icon/splash dimensions, rejects non-channel paths and verifies archive integrity. |
| Companion | 16 tests against real local HTTP, SQLite and workers: token auth, source URL redaction, ranges/HEAD/416, invalid schedules, schedule/download restart, queue progression, pause/resume, ignored Range restart, transient failures, stop/delete/path protection, stream limits, playback reservations/expiry/restart grace, session release versus delayed renewal, and real FFmpeg recording verified by ffprobe for audio and video. |
| Visual inspection | Simulator screenshots of branded login and six-channel guide checked for readable text, visible focus, viewport layout and footer spacing. These use an in-memory fixture only; no sample provider is shipped. |

Review findings were reproduced before fixes, then retested: live-search crash, stuck companion task after playlist change, Previous across playlists, late series response changing pages, uncoordinated provider streams, stopped-item retry, guide page-boundary navigation and clipped recording end times. Additional runtime/visual checks caught URL encoding, keyboard array behavior, font selection, caption track IDs, small program cells, background fullscreen focus and delayed lease response behavior.

The Roku package contains only manifest, production sources/components and branded images. It contains no tests, dependency folders, companion database, pairing token or configured provider credentials. Android/Apple source and updater manifests have no changes relative to the baseline.

## Physical Roku acceptance — not yet run

The simulator does not decode streams like a Roku, certify a Store channel, emulate every native field, or prove hardware performance. No physical Roku or private provider account was available in this workspace. Before treating 0.1.0 as a production release, run these checks on the target model and provider:

1. Sideload the ZIP. Confirm launch, full text/branding, remote focus, keyboard punctuation, Back/cancel and saved credentials after reboot.
2. Sign in with Xtream and M3U; browse all eight sections, categories, favorites, three playlists, literal search, series/seasons/episodes, and empty/error catalogs. Switch playlists during requests; verify no old provider content or Previous channel appears.
3. Play provider HLS and supported TS/VOD on the actual model. Check audio, captions, unavailable/unsupported streams, retry, preview/fullscreen/Back, channel changes, native pause/rewind/live return and VOD resume after restart. Measure the available buffer rather than assuming thirty minutes.
4. Refresh the guide while navigating/playing, check long programs across three-hour windows, guide gaps, local clock/DST, six-row page boundaries and guide return position.
5. Pair the companion and set the real provider stream limit. Record now, a future program and a manual five-minute slot. Confirm full duration, saved playback, stop/delete prompts, pause/resume downloads, and playback yielding before a scheduled recording. Repeat with two/three streams only if included in the subscription.
6. Disconnect/restart the companion and provider during jobs/playback. Verify reservation expiry stops provider playback, scheduled work survives restart, partial downloads remain valid, and retries produce playable files. Check recordings containing an interruption for expected gaps/discontinuities.
7. Soak Live TV for several hours while navigating, and run recordings/downloads with available disk space. Test a large real catalog/XMLTV feed and registry limits. Observe memory, remote responsiveness, startup time, codec errors and disk growth.

Persistent DVR/download storage requires the companion computer/NAS. Native buffer size/codec compatibility, interruptions, third-party stream usage and Store distribution remain explicit platform boundaries. This report records host verification, not physical-device approval.
