# RYZOD 4.79 — Compact Guide and Buffer Health

- Move the RYZOD mark to the upper right and guide refresh with provider explanation to the upper middle.
- Large green category heading; smaller preview, tighter guide rows and compact program times show more channels.
- Add 0/20/30-second live startup cushions. Stream buffer Off removes deliberate startup/recovery waits, while preserving the decoder queue.
- Keep live playback at normal speed and refill continuously. Natural and Steady have distinct recovery goals; actual reserve is bounded by provider availability and device memory.
- Show actual Buffer ahead, refill/start goals, refill trend and distance behind live where available. Raw feeds without a live clock are labeled unknown.
- Remove the redundant mini-guide LIVE action during direct watching. End Pause is offered only when temporary pause history exists.
- Preserve pause-only DVR, provider stream limits, saved settings/playlists and low-RAM sample cap.

Install over your existing RYZOD app. Do not uninstall or clear data. Buffering settings apply on the next tune after stopping the old preview.

For comparison on a weak channel: select 60s refill and 30s cushion, open the same channel, then open the mini guide and watch the measured Buffer ahead. A goal is not a guarantee: a source delivering too slowly can still empty the reserve. Zero means no extra wait, not a decoder with zero sample data.

Signed universal and Fire TV APKs retain package com.easyiptv.player and the permanent release certificate. Version 4.79 / 103. The release workflow runs both complete regression suites, lint, signature/package/SDK checks and native/APK alignment before publishing hash-matched updater records.

Physical Fire Stick, real-provider interruptions and USB playback still require device verification. Research and settings audit: docs/RYZOD-4.79-buffer-research.md.
