# Playback Back Implementation Plan

**Goal:** Movie/episode Back first hides playback controls; with controls hidden it asks whether to stop watching.

**Architecture:** One PlayerScreen Back action shared by AndroidX Back, the top-bar arrow and the Fire TV priority key route. Dialog cancellation preserves player/position/play state; explicit Stop saves progress, releases playback, and returns through the original navigation callback. App must not recreate a mini player for a released engine. Live TV keeps existing mini-guide/list behavior.

**Tech stack:** Existing Kotlin Compose/Media3 app, Robolectric Android UI tests, signed GitHub Actions release.

**Spec:** User's November 1 request. Direct implementation authorized by standing preference to proceed without repeated approvals.

- [x] Reproduce immediate movie exit with real AndroidX Back and Fire TV dispatch; add regression cases for hidden controls, cancellation, confirmation, episodes and touch arrow. Test held Back owns repeat/release.
- [x] Implement shared action, safe default confirmation focus, confirmed progress save/release and released-player mini guard.
- [ ] Run focused regressions and existing live-control tests, review independently, then full Android/Fire suites, lint/sign/alignment/publisher gates.
- [ ] Publish immutable 4.86/code110 from canonical 4.85 source, verify public APK hashes/certificates/source and both updater manifests.

## Verification ledger

- Reproduced 11/11 failures against original behavior (10 UI cases on API28/36 plus held-Back ownership). Initial harness setup errors were corrected before accepting those failures.
- First fix exposed default-focus and episode-state lifetime cases. Independent review identified the same episode closure issue. Added an episode-transition regression and observed it fail before replacing URL-keyed dialog state with stable state plus reset on transition.
- Cancellation is explicitly focusable even in touch input mode, so the default Keep watching request works for both phone and remote input.

- Final focused suite: 33 tests passed (12 VOD Back cases on API28/36, 9 control/ownership cases, 12 existing Live TV cases). Episode-transition regression and default-focus assertions are green.
- Publisher suite: 238 offline tests passed with actual JDK21 signing tools. Legacy guide/live contracts: 14 passed. Actionlint passed release486.yml.
