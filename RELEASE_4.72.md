RYZOD 4.72 (build 96) improves Movies, Series and live playback on Android and Fire TV.

- Movies and Series each have an Update button, including when their catalog is empty. Updating one section preserves the other and the live lineup; a failed provider request keeps saved titles and shows a retry message.
- Movie artwork accepts nonblank alternate provider image fields, including root-relative image links. The same corrected artwork feeds Movies and Search and survives cache reloads.
- Search Done closes the orange keyboard and moves focus into available results. Remote key release can no longer reopen the field immediately.
- Live playback checks rendered-video progress every five seconds while visible and playing. A stalled picture with advancing playback triggers bounded recovery of the same channel. Pause, buffering, background playback and surface changes do not trigger this recovery. Temporary recording history and recording ownership are preserved.
- Provider HLS links are retained as supplied. Added DASH, SmoothStreaming and RTSP playback modules alongside HLS and progressive formats; updated Media3 from 1.9.0 to 1.9.4. Hardware decoding remains first with decoder fallback and the existing FFmpeg audio extension.
- Catalog network reads and parsing stop when cancelled, reducing competition with playback. Refresh persistence no longer cancels itself when the UI receives new data.
- Guide navigation brings clipped rows fully into view before moving focus, avoiding repeated scrolling at the viewport edge. Forward, reverse and boundary navigation are covered by remote-input tests.
- Download queue dispatch and service cleanup use explicit ownership. Resumed downloads validate Content-Range before appending, preserving partial files if a provider returns an incorrect range.
- The alternate launcher icon now uses the same complete RYZOD wordmark as the primary icon and TV banner.

Install over your existing RYZOD app. Movies with missing artwork should be refreshed with Update Movies. Launcher tile sizing and icon caching remain controlled by Fire OS. Playback quality and codec availability depend on the device and source; this update cannot restore detail absent from a low-bitrate provider stream.

Validation: 96 unit, Compose/Robolectric and local HTTP regression tests passed, plus three release-contract checks. Release compilation succeeds; full Android lint completes with zero errors and 33 nonfatal warnings. The publishing workflow reruns tests and lint, checks package/version and the permanent signing certificate, and enables both updater manifests only after verifying the uploaded APK digest. Physical Fire Stick behavior, HDMI/display output, actual provider outages and TV standby require device confirmation; automated tests cannot certify every channel or codec.
