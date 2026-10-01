# Execution ledger — plan: docs/superpowers/plans/2026-09-30-ryzod-apple.md
- Source: v4.70 commit 637c449; branch already isolated from main.
- User: explicitly asked to build after ordering the iPhone; earlier preference authorizes continuous work without repeated approvals.
- Pre-flight: provider signatures and models shared by core and UI are defined in plan; no interface conflicts found.
- RED: Mac workflow 36818819295 compiled test scaffolding; 14 tests ran, 13 test cases failed with 29 assertions/uncaught expected missing-implementation errors. No compiler error or crash. Invalid-input test passed against throwing scaffold and will be reinforced during review.
- Decision: native AVPlayer defaults Xtream live URLs to m3u8 because Android raw TS playback/FFmpeg is not an Apple compatibility guarantee.
- Signing remains dependent on the user's Apple Developer account; unsigned builds must never be described as sideloadable.

- Task 1: core GREEN on Mac run 36819206637: all 14 tests passed, 0 failures; exact runtime failures from RED are now resolved.

- Review: fresh whole-branch reviewer found ATS override, previous-account playback history, stale cached guides beyond channel 20, positional M3U favorites, and XML error responses being accepted as XMLTV. Added regression tests before fixes. Core review RED run 36820601869: 18 tests, 5 failures across three cases, as expected; its app compilation exposed SwiftUI Category ambiguity and shadowed episode error binding.
- Compile repairs committed separately as 4f0fddaea98f8fffae72199a2e49cd1ba98acf6e. Review RED temporarily permits a failed core step to reach native session tests; final workflow must remove that setting.
- Fix follow-up review: no material blocker found in account/guide epochs, three-request queue, stable playlist IDs, XMLTV root validation, or stop/history reset. Native session tests add delayed account cancellation, channel 25 refresh, XMLTV retention, playback account reset, ATS rejection and guide concurrency.
- Deferred: physical playback/codecs, provider text dates without offsets, long playback/battery testing, signing and TestFlight distribution.

- Native RED run 36820878092 on iPhone SE 3 simulator / iOS 26.2: unsigned device build succeeded; all four new session regression tests failed for their intended reasons (ATS -1022, stale channel 25, old previous channel, XMLTV error replacing guide). Two login UI tests passed. The final code removes temporary continue-on-error, fixes those behaviors and adds cancellation/concurrency tests.
