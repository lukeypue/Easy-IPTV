# RYZOD 4.89 — Faster Startup

- Saved channels become available as soon as they load from disk, while RYZOD refreshes the lineup from your provider.
- If saved channels are unavailable and the provider is slow, the splash screen gives way to the app menus after five seconds. The Live TV page shows its own loading status.
- RYZOD restores saved guide listings separately so they can finish loading after the app opens. A background lineup refresh does not request a fresh guide focus just because the number of channels changed.

Fire TV uses `RYZOD-v4.89-FireTV.apk`. Android and Android TV use `RYZOD-v4.89.apk`.
