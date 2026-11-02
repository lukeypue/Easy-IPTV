# RYZOD 4.87 — Stream Health

Version 4.87 · Android version code 111 (`0x6f`) · package `com.easyiptv.player`.

- Optional, on-demand Stream health diagnostics use the existing playing stream, without opening an extra provider connection. They show incoming Mbps, advertised channel demand or “unknown” when unavailable, buffer, dropped frames and stream format. These observations do not automatically blame the provider or server.
- A 720p quality option is offered only when the stream has a real, compatible alternative rendition. Fixed-quality streams explicitly report that the option is unavailable. There is no local transcoding or default software decoding. Auto remains the default. Smooth now adjusts more promptly among actual compatible quality versions, including 480p/720p/1080p when the provider supplies them.
- Separate **Dual Stream Trial** switch, off by default: compatible HLS feeds race two complete copies of each segment and use the first successful copy. This can cover independent delivery gaps; it does not merge arbitrary TS packets or repair a shared upstream outage. Requires two available provider slots, may use extra data and add live delay. Unsupported feeds use one connection. Stream health reports trial status.
- Retains the 4.86 movie and episode Back behavior: Back first hides playback controls, then opens “Stop watching?” with “Keep watching” selected by default. Cancelling preserves the playing or paused state; confirming saves position, releases the stopped stream and returns to the original catalog. Touch, system and Fire TV Back share this behavior; Live TV retains its mini-guide behavior.
- Retains keyboard focus and full keyboard action words, violet bubbles with white lettering, the 30% larger text scale with accessibility font scaling, roomier layouts, Smooth playback, caches, saved guides and search retention.

Release builds use `ryzod-v4.87-stream-health`, retaining the 4.86 source lineage and the canonical 4.83 source branch, `ryzod-v4.83-colors`; the older app source on `main` is not the build source.

Install `RYZOD-v4.87.apk` for Android/Android TV or `RYZOD-v4.87-FireTV.apk` for Fire TV. The package and permanent signing identity are unchanged.

The release requires the complete, unfiltered Android and Fire TV regression suites (at least 344 tests per distribution), lint, signed package verification and native 16 KB alignment checks. Local focused regressions cover 119 cases; full distribution verification is enforced before publishing. Physical-device validation remains separate.

Amazon/Play candidates are retained as workflow artifacts only and are not submitted to stores. Play AAB verification requires the permanent certificate, strict signature coverage and exact package/version/SDK identity. Only the two APKs and the exact-HEAD source ZIP are public release assets. Both updater manifests on `main` are promoted atomically with monotonic version checks after all release gates pass; an existing tag must match the workflow source commit before reuse.

UI screenshots produced by the toolchain are retained as workflow artifacts for visual review when available; they are not public release assets. Physical-device appearance, keyboard focus, diagnostics and provider playback still require device validation.
