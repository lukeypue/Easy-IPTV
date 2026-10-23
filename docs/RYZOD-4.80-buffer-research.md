# RYZOD 4.80 playback and platform review — 2026-10-23

Direct live playback and low latency are separate. RYZOD's normal live path uses the provider feed directly and does not continuously write disk history. This removes disk work; it does not make a zero-wait stream more resistant to network jitter. Pause alone starts temporary Android history.

## Primary references checked

- YouTube latency: https://support.google.com/youtube/answer/7444635?hl=en — normal latency offers the least viewer buffering; lower latency trades away protection against interruptions.
- Media3 live parameters: https://developer.android.com/media/media3/exoplayer/live-streaming — target offset, startup threshold and buffered duration are distinct. Media3 can adjust speed, but RYZOD constrains ordinary live playback to 1x. A finite live window can limit available data.
- Stable Media3: https://developer.android.com/jetpack/androidx/releases/media3 — stable 1.11.1; 1.12.0-alpha01 is a preview, not a stability recommendation. 1.11.1 has HLS EOF retry and renderer-stall fixes. Upgrade must pass real load-control and decoder compatibility checks, not merely dependency resolution.
- Shaka: https://shaka-project.github.io/shaka-player/docs/api/tutorial-network-and-buffering-config.html — refill and rebuffer goals serve different purposes.
- Apple AVPlayer: https://developer.apple.com/documentation/avfoundation/avplayeritem/preferredforwardbufferduration — a duration preference; **0 asks Apple to select its own buffer**, not no buffering. Lower values increase stall risk. AutomaticallyWaitsToMinimizeStalling and playImmediately govern waiting separately. Read official DocC JSON for full discussion.
- Roku Video: https://developer.roku.com/dev/docs/video — buffering percentage is not seconds of playable forward video; pause-buffer fields describe paused live history, not the ordinary download queue. Streaming-segment latency is milliseconds from the available edge.
- Roku metadata: https://developer.roku.com/dev/docs/content-metadata — negative PlayStart requests a start behind the live edge. This is a live-delay preference, not a guaranteed forward reserve. No documented arbitrary Android-style forward-buffer goal was found. Do not simulate it with a timer or unsupported metadata.
- Apple HLS: https://developer.apple.com/streaming/ — adaptive renditions/CMAF/LL-HLS require provider support. RTMP, RTSP and low latency are not universal reliability upgrades. Keep supplied URLs and use supported renditions when the provider supplies them.

## Android findings

4.79 already supports startup 0/3/4/6/10/20/30 seconds and refill Off/10/30/60. Natural recovery is max(2×startup,8s), capped40s; Steady max(3×startup,15s), capped50s. Refill expands for recovery headroom up to60s. Thus a selected small refill may have a larger effective target, and Immediate startup does not bypass recovery. Off does. Show the effective numbers, make Off's override explicit, and add20s refill without changing saved preferences.

Actual buffer can be below its target because of bitrate, low-RAM allocation cap, live-window size or insufficient delivery. Twenty seconds of actual video covers approximately twenty seconds without replacement data; a setting or loading-screen duration does not prove actual video acquired. A source delivering slower than playback will eventually drain any finite reserve.

Independent audit and review found HTTP403/406 compatibility fallback overriding an explicit Pause, plus stale error retries surviving new VOD selection: open() did not advance playbackGen/reset retries for on-demand playback, so an old callback could prepare/play a new paused movie. Verify both new-session cancellation and explicit Pause through regression tests.

## Review boundaries

Run full automated suites for phone and Fire distribution, lint, release build, identity/signature and native alignment checks. The audit covers settings/navigation/playback and existing ownership/recording/download tests. It does not prove absence of every bug or actual provider/physical device performance. Apple/Roku native capabilities differ; parity must name unsupported features rather than offer inert settings.

The engine migration uses Media3 1.11.1 and matching Kotlin 2.2.0 metadata support. Existing UI dependencies are retained to isolate playback changes. All current per-player LoadControl default methods were compared against the official 1.11.1 source; forwarding remains explicit. FFmpeg audio decoder 1.9.0+1 remains the published extension and must survive the release shrink/link and native alignment checks.
