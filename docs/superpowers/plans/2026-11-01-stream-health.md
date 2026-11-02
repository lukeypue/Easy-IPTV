# Stream health Implementation Plan

**Goal:** Show truthful on-demand playback diagnostics and a reversible 720p limit for streams with genuine alternatives.

**Architecture:** Bounded passive transport meter plus pure assessment, Media3 quality controller, reusable dialog reading the running Playback engine, guide entry and INFO shortcut. Snapshot polling is scoped to the dialog; no extra provider connection.

**Spec:** docs/superpowers/specs/2026-11-01-stream-health-design.md

- [ ] Add transport/classifier and quality tests; observe their failures before implementations, then verify realistic healthy/idle/starving/local/reset/unsupported cases.
- [ ] Add guide-entry/player INFO/UI regressions before integration; preserve focus, playback and Back behavior; sample formats/buffer/counters honestly.
- [ ] Integrate session reset/HTTP transfer listener and quality reset into existing Playback; show unknown/paused/local states without false server diagnoses.
- [ ] Review independently, inspect phone/TV panel, run focused and full Android/Fire suites, lint/sign/alignment/publisher gates.
- [ ] Publish4.87/code111 and verify public packages, exact source ZIP and both updater manifests. Include platform research with primary sources.

## Accepted steering and verification ledger

- User requested adaptive Smooth across real 480/720/1080 renditions, then explicitly requested a separate off-by-default dual-stream experiment for independent gaps. Scope added to design: complete HLS-segment races, safe unsupported fallback, provider-budget reservations and cancellation/memory guards.
- RED: 15 health API tests +6 UI cases failed before implementation; 15 quality tests failed before implementation; 10 adaptive-selector tests failed before implementation; 19 dual-transport +8 policy +2 playback-budget cases failed before implementation.
- Health and quality first integrated run: 30/30 unit cases passed; two guide UI failures were ambiguous selectors, corrected by scoping to the dialog. Remote-scroll testing added to the panel.
- The experimental trial is never enabled by default and never joins unmatched continuous TS packets. Normal/VOD networking remains unchanged unless the user allows the compatible HLS trial.

- Independent review found that manifest/key loads could exceed the two-request ceiling and that segment prefetch could appear to be a delivery interruption. Added shared request admission and an explicit pending-observation state; regression tests cover both.
- Additional transport regressions cover unread completed payload admission, oversized streaming cancellation, loser cleanup, bounded payloads, close and budget reductions. Fixed stream read/close synchronization without blocking the UI.
- Phone and TV screenshots reviewed at the enlarged application font scale; details scroll with the remote while quality controls and Close remain accessible.

- Local final focused suite: 119 tests, zero failures/errors/skips. Existing guide contracts: 14 cases passed. Offline publisher suite: 283 cases passed. Release workflow actionlint passed. Full 344-case distribution suites, signed packages and public verification are still gated by CI.
