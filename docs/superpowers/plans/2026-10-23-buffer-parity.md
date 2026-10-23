# RYZOD buffering and platform parity implementation plan

Goal: finish Android/Fire TV first, then bring existing native ports toward the same guide and buffering controls.
Architecture: preserve native players, original provider URLs, single player ownership and pause-only DVR. Zero means no deliberate wait, never zero decoder data. Use actual player measurements rather than timers as proof of buffered media.

1. Android: expose a 20-second refill goal and a clearly named real-time zero-wait choice; show effective start/recovery/refill values and explain when Off overrides other choices. Preserve saved preferences. Add UI regression checks, run complete phone/Fire suites, lint and signed builds. Evaluate stable Media3 1.11.1 with real allocator tests; retain software audio decoding.
2. Publish Android 4.80/build104 only after checks and independent review pass. Verify public assets and updater hashes.
3. Roku: inspect existing SceneGraph implementation and current buffering API, implement supported matching controls and guide navigation/layout, test BrightScript and companion, compile and package. Do not manufacture a 20-second buffer with a wall-clock delay.
4. Apple: inspect existing iOS implementation, add native buffering controls with actual loaded-range measurements, match guide presentation, add tvOS build support where platform APIs allow. Run core tests and macOS build/simulator CI. Document unsupported features and signing/hardware requirements honestly.

Review focus: stale retries after channel switches; zero override versus retained preferences; finite HLS windows and low RAM; Apple zero property meaning automatic rather than immediate; Roku API units and supported stream formats.

Execution: inline under existing user authorization to go forth. Independent review before release. No claim of bug-free or hardware-tested playback without device evidence.

Android focused verification: Media3 1.11.1 compiled; four focused suites passed with zero failures/errors/skips. UI and retry regressions observed RED before fixes. Independent review findings (403/406 Pause and stale-session cancellation assertion) fixed and covered. Python guide contracts 3+11 pass. Full regression/lint and signed publication gates remain before release.
