# RYZOD 4.82 implementation plan

The user authorized keyboard improvements, search retention and remote-input fixes, persistent guide data, and a separate Smooth control. The implementation starts from the complete 4.81 release branch; main's old application source is not a build baseline.

1. Use 60% of TV width for the keyboard, retaining usable phone width. Use a warm orange panel, dark keys, cream letters, and distinct golden focus. Require a fresh matching confirm press and release; navigation cancels pending activation.
2. Retain the last search query, bounded results, and vertical scroll position for the active playlist across playback and series navigation. Allow heavy search indexes to be reclaimed during playback.
3. Cache the guide privately and atomically, with size limits and isolation by guide URL hash. Show the last valid schedule during refresh, after refresh failures, and after process restarts. Recheck freshness on resume and while browsing.
4. Give Smooth its own on/off switch. Preserve manual buffer, startup, and recovery preferences for when Smooth is off. Explain the tradeoff directly beneath the switch without promising to repair insufficient provider or network throughput.
5. Reproduce the input and retention bugs with regression tests. Run the complete Android and Fire TV suites, inspect keyboard screenshots, obtain independent code review, and verify signed release packages and updater manifests before reporting completion.
