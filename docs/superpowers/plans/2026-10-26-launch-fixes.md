# RYZOD 4.81 implementation plan

Goal: implement the user's authorized Smooth option, season drilldown, Android drawer correction, and prelaunch audit on the complete 4.80 lineage.

Architecture: preserve the shared player, direct provider playback, pause-only history, provider connection ownership, and existing UI. Smooth is opt-in and bypasses added live waits and optional guide/status work; essential decoder queues and stall recovery remain. Existing normal/steady choices remain available.

Tech stack: Kotlin, Compose, Media3, Robolectric, Android Gradle.

- [x] Add regression coverage for Smooth settings/policy, season drilldown/back/queue ordering and drawer top controls on phone and TV.
- [x] Run new regressions before implementing and inspect expected failures.
- [x] Implement Smooth preference, zero startup/recovery waits, small forward decoder queue, zero retained past samples, no mini guide/EPG request/status polling. Keep pause and recovery and accessible exit controls.
- [x] Replace flattened season sections with a season list followed by that season's episodes. Sort season/episode queue numerically and key state to source and series. Back returns episodes > seasons > series.
- [x] Open drawer at top when early categories selected, preserving focus and bringing later categories into view.
- [x] Run full direct + Fire TV tests, lint and build. Obtain independent AI code review and address concrete issues.
- [x] Update version/release workflow and updater publisher only after verification. Report real-device and store/billing/platform blockers accurately; do not claim access to every historical conversation.

Review focus: zero settings with old steady preference; VOD startup unaffected; empty seasons and source changes; menu at narrow portrait size; pause-only DVR and provider retry ownership; release signing and updater identity.
