# RYZOD 4.92 — Smooth live and stream trial

- Smooth uses a single connection and adds no startup or recovery cushion. After an interruption, it can seek forward in a moving live playlist. If it cannot verify a forward position, it offers Try again instead of automatically reopening the old feed.
- With Smooth off, the optional HLS stream trial can use up to two or three of the free slots within your account limit. It races copies of the same segment and can complete a truncated copy if the server returns the exact missing byte range with the same strong ETag.
- The trial uses extra data and can add live delay. Raw feeds and servers without the required segment support cannot supply verified replacement bytes.

Fire TV uses `RYZOD-v4.92-FireTV.apk`. Android and Android TV use `RYZOD-v4.92.apk`.
