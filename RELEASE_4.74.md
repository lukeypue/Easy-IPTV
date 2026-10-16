# RYZOD 4.74 — Live TV stability and launcher repair

- One Live TV mode. Ordinary viewing stays direct; temporary history starts only on Pause or an explicit recording request. Keep recordings, pause/resume, and the bounded thirty-minute pause history.
- Intercept the complete OK/Enter press before the player view. Opening the mini guide cannot release that same press onto its Pause control. Normal channel-key repeats remain available.
- Replace the obsolete Smooth/DVR switch and misleading DVR RETRY status with LIVE and clear pause-history status. LIVE returns to direct playback and clears temporary history when no saved recording owns it.
- Require actual written TS data before starting the pause reader. A stalled resumed reader gets at most two local reopen attempts before returning to direct Live TV; an intentional pause is never automatically resumed. Preserve a writer owned by an active saved recording.
- Stop Recording asks for confirmation from the mini guide and phone player controls. Play/Pause labels follow the requested state during buffering.
- Search scans remain off the UI thread, coalesce rapid edits, and reuse prepared word boundaries. Down from the keyboard's bottom row moves into results while leaving the keyboard open; Up from the first result row returns to the keyboard. Side/top arrows remain inside the keyboard.
- Replace legacy plain-R launcher fallbacks with the approved complete artwork. Fresh manifest icon/banner resources select the square logo for phones and full-width artwork for TV.

Validation results and remaining device checks are recorded in docs/RYZOD-4.74-validation.md. A permanent-key signed update is created only by the release workflow; a local unsigned APK must not replace an installed production build.
