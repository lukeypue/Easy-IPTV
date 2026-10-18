# RYZOD 4.79 buffering and settings review

## Findings from the released code

4.78's stream buffer is a forward refill goal, not its startup wait. A 30-second goal can begin playing after 4 seconds (6 in Steady). The startup preference also silently clamps at 12 seconds. The shared load control applies that live startup choice to on-demand items. The default Media3 live logic can reduce a startup/recovery threshold to half the target live offset, and a finite provider playlist or the Fire Stick allocation target can constrain the achieved duration. Neither a configured goal nor time spent on a loading screen proves that many seconds of playable data were acquired.

## Primary-source research

- YouTube: https://support.google.com/youtube/answer/7444635?hl=en — More read-ahead protects viewers from delivery fluctuations. Normal latency has the least viewer buffering; ultra-low latency sacrifices that cushion. YouTube's Buffer Health is actual buffered video, not a configured goal. This supports exposing measured buffer health and allowing a longer startup cushion.
- Google Media3: https://developer.android.com/media/media3/exoplayer/live-streaming?hl=en — Adaptive streams have moving live windows and an optional measurable current-live offset. Progressive live streams have no such window. Live speed control can adjust latency. RYZOD retains 1.0x playback and never pretends a progressive queue duration is a wall-clock live offset.
- Shaka Player: https://shaka-project.github.io/shaka-player/docs/api/tutorial-network-and-buffering-config.html — Separate refill goal, startup/recovery goal and retained past buffer. Recovery should be below refill capacity. Network retry behavior is a different setting. This supports distinct controls and consistent headroom.
- Kodi: https://kodi.wiki/view/Settings/Services/Caching — Cache size and read rate are separate. Adaptive read factor increases acquisition when the cache is low. A player cannot acquire faster than its source delivers; increasing memory alone does not fix sustained under-delivery.
- Apple HLS: https://developer.apple.com/streaming/ — HLS supports adaptive bitrate delivery. CMAF and low-latency HLS are packaging/delivery options requiring origin support, not a player-side repair switch. Lower delay is not a promise of fewer stalls.
- Media3 supported formats: https://developer.android.com/media/media3/exoplayer/supported-formats — Container, delivery protocol and codec are separate. Existing Media3 dependencies cover HLS, DASH, SmoothStreaming, RTSP and progressive formats. Do not rewrite provider URLs or force a new format that may not exist.

- Current engine release notes: https://developer.android.com/jetpack/androidx/releases/media3 — Stable 1.11.1 includes fixes for secondary-renderer prewarming, audio offload transitions and HLS EOF retry handling. RYZOD currently uses 1.9.4. The reproduced startup-goal/measurement faults are app policy faults, so this release keeps the tested engine version; a separate engine migration can isolate any new API or decoder changes from these policy repairs.
- Emerging transport: https://shaka-project.github.io/shaka-player/docs/api/tutorial-moq.html — Media over QUIC support is experimental in Shaka and needs a matching origin. It is not a substitute protocol that can be forced onto an ordinary IPTV URL.

TiviMate/Sparkle internals are not publicly documented in enough detail to claim their exact buffering algorithm. Third-party IPTV marketing pages were excluded as technical evidence. No unverified copy of another player's behavior or automatic format substitution is introduced.

## Practical interpretation

A real 30-second playable reserve can cover a short interruption longer than a 10-second reserve. It cannot make a persistently slow stream sustainable. Playback at normal speed consumes one second of video per second; if replacement video arrives slower, the reserve shrinks. HLS windows, keyframe readiness and memory limits can prevent a requested cushion from being achieved. Keep those escape paths to avoid endless startup on constrained hardware; expose actual duration so the limit is visible.

The new 0-second choice removes deliberate startup/recovery waiting. It retains a small decoder/refill queue; zero buffering in the literal sense is not possible. The 20/30-second startup choices preserve older saved choices. Natural/Steady differ after starvation, not in playback speed. Ordinary watching remains direct playback; disk history still starts only on Pause.

## Settings audit

| Setting | Behavior and validation |
| --- | --- |
| Stream buffer | Off/10/30/60. Off means no added wait, with a small decoder queue. Refill continues while playing; capacity can expand for startup/recovery headroom. |
| Channel lock-in | 0/3/4/6/10/20/30 seconds. Explicit startup goal; memory/provider limits may permit earlier start. |
| Natural/Steady | Recovery goals 8–40 / 15–50 seconds, based on selected lock-in. Normal speed in both; Off bypasses waits. |
| Buffer health | Actual sample duration ahead, configured goal/start, measured trend. Live offset only when the provider exposes it. |
| Provider connections | Existing one-stream ownership, recording/download budgeting retained. No speculative parallel probes. |
| AFR | Existing separate live/VOD choices; refresh matching affects display cadence, not network capacity. |
| Decoder fallback/CC/size | Existing renderer fallback and provider-dependent captions preserved; stretching changes display geometry only. |
| Pause storage/USB/retention | Existing write destination, pause-only history and delete/stop confirmations retained. Hardware USB checks still required on device. |
| Updates | Same package and permanent certificate; new version, per-distribution signed APKs and hash-matched manifests. |

## Device verification

Automated tests cannot prove the user's provider throughput or Fire Stick hardware playback. After install, compare 10s and 30s cushions on the same problematic channel with a 60s refill goal. Open the mini guide: record actual Buffer ahead, trend and Behind live (or unknown). A falling ahead duration without speed-up identifies depletion, not automatic catching up. A 30s goal with only a few actual seconds needs bitrate/memory/provider-window investigation.
