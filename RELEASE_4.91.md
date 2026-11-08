# RYZOD 4.91 — Live recovery

- Count a live feed ending early toward the reconnect limit so a channel that keeps closing cannot restart indefinitely after short runs.
- After automatic reconnection on a channel that had already started, use the configured recovery cushion before resuming. Your lock-in setting still applies when you first tune a channel.
- Show Try again if repeated recovery attempts fail.

Fire TV uses `RYZOD-v4.91-FireTV.apk`. Android and Android TV use `RYZOD-v4.91.apk`.
