# Stream health and real quality alternatives

The user authorized an on-demand diagnostic panel and research into major streaming platforms. Ethernet and channel-specific failures across two players suggest a shared feed/path/format issue, without proving server overload. No extra connection, speed probe, transcoding or persistent diagnostic polling should increase Smooth overhead.

Use passive transfer counters on the existing remote media connection, with bounded rolling samples, and show incoming payload Mbps, advertised combined audio/video bitrate when known (otherwise unavailable), playable reserve, recent video-frame drops, codec/resolution/FPS, playback/error status and cautious delivery/decoder observations. A healthy paced live stream need not download faster than its bitrate. Idle/full-buffer and pause states must not be flagged as delivery starvation. Loopback, local files and DVR pause playback must never masquerade as provider delivery. No URLs, credentials or server-load claims are displayed or stored by telemetry.

Access Stream health next to Update Guide while the channel keeps playing, including Smooth. An optional INFO-key shortcut may open the same dialog without changing the established Menu/CC shortcut. Close/Back returns focus and preserves playback. Poll the panel at 1 second only while open; transport sampling itself uses counters and small bounded buckets.

An optional 720p limit selects real supported alternatives within the selected video group. A fixed-quality feed explicitly shows that lower quality is unavailable. Current playing format remains independent from requested limits. Auto stays default; no automatic downgrade or software video decoding. Restore video constraints/overrides on mode reset/new channel/VOD, preserving captions and audio preferences.

Approaches considered: another global overhead switch would not identify delivery failures; locally resizing/transcoding a single source would not reduce incoming data and adds work. Chosen implementation observes the current stream and offers genuine alternatives when supplied. This follows the user's approved panel proposal and standing instruction to proceed without repeated approvals.

Preserve canonical 4.86 Back/confirmation behavior, live guide navigation, Smooth buffer policy, saved searches/guide, typography and colors. Release4.87/code111, permanent package/signature unchanged; full suites and signed-release gates remain required.

## Accepted steering: adaptive Smooth and isolated dual-stream trial

The user additionally requested automatic quality changes and a separate dual-stream experiment. Smooth uses Media3's bandwidth/buffer-based adaptive selector for genuinely supplied variants, with a 3-second upshift/5-second downshift policy compatible with its 5-second reserve; ordinary/VOD selection remains unchanged. No synthetic quality versions are claimed.

Dual Stream Trial is separately opt-in, off by default, and reserves two provider stream slots only for compatible HLS with budget available. Race copies of the same complete media segment; choose the first complete successful response and cancel the other. Never mix unmatched raw MPEG-TS packets. Range requests/manifests/keys/init segments remain ordinary single requests; oversized segments use a bounded single-response fallback. One racing pair at a time, bounded memory, cancel calls on close/tune/background/pause. Full-segment racing can add latency and data use; neither path can recover video the shared source never supplies. All other formats remain single-stream with an explicit status. Incoming rates in this mode describe usable winner payload, not duplicated wire traffic.
