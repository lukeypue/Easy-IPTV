# RYZOD 4.86 — Playback Back

Version 4.86 · Android version code 110 · package `com.easyiptv.player`.

- During a movie or episode, Back first hides the fast-forward, play and rewind controls. With controls hidden, Back opens “Stop watching?”. “Keep watching” is the default; cancelling preserves the current playing or paused state. “Stop watching” confirms a return to the original catalog and saves the playback position and releases the stopped stream before returning through the existing `onBack` path.
- The touch Back arrow, Android system Back and Fire TV Back follow the same behavior. Live TV keeps its existing mini-guide Back behavior.
- Keep full keyboard action words visible with Android’s larger accessibility text settings.
- Keep keyboard focus in the search field while typing, without jumping to results.
- Use violet backgrounds and white lettering for menu and category bubbles.
- Use a 30% larger app text scale, preserving accessibility settings and platform font-scaling behavior, with roomier layouts for larger text.

Retains the 4.85 keyboard focus, violet styling, larger text and cache behavior, along with the playback, Smooth, saved-guide and search-retention behavior of the canonical 4.83 source branch, `ryzod-v4.83-colors`. Release builds use `ryzod-v4.86-playback-back`; the older app source on `main` is not the build source.

Install `RYZOD-v4.86.apk` for Android/Android TV or `RYZOD-v4.86-FireTV.apk` for Fire TV. The package and permanent signing identity are unchanged.

The release requires the complete Android and Fire TV regression suites (at least 258 tests per distribution), lint, signed package verification and native 16 KB alignment checks. Amazon/Play candidates are retained as workflow artifacts only and are not submitted to stores. Only the two APKs and the exact-HEAD source ZIP are public release assets. Both updater manifests on `main` are promoted atomically with monotonic version checks after all release gates pass.

UI screenshots produced by the toolchain are retained as workflow artifacts for visual review when available; they are not public release assets. Physical-device appearance, keyboard focus and provider playback still require device validation.
