# Execution ledger — plan: docs/superpowers/plans/2026-09-30-ryzod-apple.md
- Source: v4.70 commit 637c449; branch already isolated from main.
- User: explicitly asked to build after ordering the iPhone; earlier preference authorizes continuous work without repeated approvals.
- Pre-flight: provider signatures and models shared by core and UI are defined in plan; no interface conflicts found.
- RED: Mac workflow 36818819295 compiled test scaffolding; 14 tests ran, 13 test cases failed with 29 assertions/uncaught expected missing-implementation errors. No compiler error or crash. Invalid-input test passed against throwing scaffold and will be reinforced during review.
- Decision: native AVPlayer defaults Xtream live URLs to m3u8 because Android raw TS playback/FFmpeg is not an Apple compatibility guarantee.
- Signing remains dependent on the user's Apple Developer account; unsigned builds must never be described as sideloadable.
