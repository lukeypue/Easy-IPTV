# RYZOD Apple preview 0.2.0 — Android 4.80 playback/guide update

The existing iPhone/iPad preview now has a live forward-buffer goal in Settings: Real time (0s), 10s, 20s, 30s and 60s. Real time requests immediate normal-speed startup while retaining a small 5-second forward preference for decoding. Automatic recovery stays enabled because disabling it can leave a stalled stream paused; Apple controls recovery waiting. Apple's preferredForwardBufferDuration=0 means automatic, not zero buffering, so it is deliberately not used as the real-time implementation.

Apple controls actual startup/recovery thresholds. A longer forward preference is not proof that the target is already downloaded. This port does not offer an inert copy of Android's Natural/Steady thresholds. Changing the setting stops an active live preview and takes effect on the next channel; movie/saved-media buffering stays independent. Preferences survive relaunch.

The player displays actual contiguous loaded video ahead, a progress line, and distance to the provider's available seekable edge when supplied. Gaps and invalid/non-finite ranges cannot inflate the displayed reserve. Unknown edge stays unknown. Automatic live-offset preservation/seeking after buffering is explicitly disabled so the player does not discard the cushion to restore its old offset.

Guide changes: logo at upper right, Update Guide centered with provider explanation, large green category text, compact 44-point rows and closer title/time spacing. The existing provider login, secure credentials, EPG, catalog, series, search and favorites remain.

## Verification and limits

The Mac workflow passed 32 tests: 21 provider/guide/buffer core, seven native session/settings and four iPhone/iPad UI executions. The unsigned device build also passed. See BUILD_STATUS.md for the exact tested commit and workflow evidence.

This remains an iPhone/iPad native preview. Local DVR, scheduled recordings, offline downloads, USB storage and Apple TV are not implemented. Apple codec support differs from Android's FFmpeg extension. Layout/settings parity does not imply these capabilities exist.

A signed installable IPA/TestFlight build still requires the owner's Apple Developer Program and distribution signing/App Store Connect configuration. The unsigned app artifact is build evidence and cannot be installed by opening a link. See INSTALL_ON_IPHONE.md. Actual provider/device playback needs the physical iPhone/iPad.

Official references: https://developer.apple.com/documentation/avfoundation/avplayeritem/preferredforwardbufferduration , https://developer.apple.com/documentation/avfoundation/avplayer/automaticallywaitstominimizestalling , https://developer.apple.com/documentation/avfoundation/avplayeritem/automaticallypreservestimeoffsetfromlive .
