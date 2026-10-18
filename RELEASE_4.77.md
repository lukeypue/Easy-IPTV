# RYZOD 4.77 — Live playback and guide

- Monitor requested foreground live playback through BUFFERING and motionless READY states, with bounded reconnects after 20 seconds without position or buffer growth. Never resume intentional Pause or background playback.
- Keep pause-only DVR; tuning does not start a recording. Add player-state diagnostics for the reported one-minute stop.
- Put the preview above the left side of the guide with program details beside it; Up from the top visible row reaches the preview. Left from channel names reaches categories, then the main menu.
- Keep the guide visible while opening the main menu; Back returns to the main menu and then asks before exiting.
- Expand the category rail while focused. Allocate guide focus objects only for composed rows, reset page/focus state on category changes, and tolerate duplicate provider IDs in All channels.
- Show Connecting when buffer progress is unavailable rather than treating 0% as proof of an offline channel.

Hardware verification still required: Fire Stick OS 8, initial tune A/V sync, uninterrupted viewing beyond one minute, preview/full-screen transitions, All channels remote navigation, explicit Pause/resume, recording ownership.

Local validation: clean Android build, all 164 phone/universal automated tests passed (zero skipped), lintDebug passed, and 14 existing guide/playback contract checks passed. Independent code review completed; the reported Back-handler conflict was reproduced in two tests and corrected. Signed universal/Fire TV APK builds and the Fire TV distribution suite will run in the prepared GitHub release pipeline before publication. No device playback verification has been performed.
