# RYZOD 4.85 — Readability

Version 4.85 · Android version code 109 · package `com.easyiptv.player`.

- Keep full keyboard action words visible with Android’s larger accessibility text settings.
- Keep keyboard focus in the search field while typing, without jumping to results.
- Use violet backgrounds and white lettering for menu and category bubbles.
- Use a 30% larger app text scale, preserving accessibility settings and platform font-scaling behavior, with roomier layouts for larger text.

Retains the playback, Smooth, saved-guide and search-retention behavior of the canonical 4.83 source branch, `ryzod-v4.83-colors`. Release builds use `ryzod-v4.85-readability`; the older app source on `main` is not the build source.

Install `RYZOD-v4.85.apk` for Android/Android TV or `RYZOD-v4.85-FireTV.apk` for Fire TV. The package and permanent signing identity are unchanged.

The release requires the complete Android and Fire TV regression suites (at least 245 tests per distribution), lint, signed package verification and native 16 KB alignment checks. Amazon/Play candidates are retained as workflow artifacts only and are not submitted to stores. Only the two APKs and the exact-HEAD source ZIP are public release assets. Both updater manifests on `main` are promoted atomically with monotonic version checks after all release gates pass.

UI screenshots produced by the toolchain are retained as workflow artifacts for visual review when available; they are not public release assets. Physical-device appearance, keyboard focus and provider playback still require device validation.
