# RYZOD 4.75 — SDK and Fire TV packaging

- Android SDK 36 target, compatible build tool upgrades, phone safe-area handling and AndroidX back navigation.
- Android background time-limit handling preserves recorded video and resumable download bytes while stopping services promptly.
- Dedicated **RYZOD-v4.75-FireTV.apk** supplies full wide artwork even to launchers loading resources with a default non-TV configuration. Install this APK over the existing app on Fire TV; keep your playlists and settings. Phone users use **RYZOD-v4.75.apk**.
- Settings → **Device & artwork check** shows the installed artwork and device/launcher details. A stale Fire home tile remains an affected-device verification item; this release does not promise to reset Amazon’s cached imagery.
- Future self-updates select the Fire TV asset when present. Amazon and Play candidates use store-managed updates and exclude the APK installer permission.
- Silence the last channel during app loading and the handoff to another app; recordings continue without reviving hidden playback. Resume/restart prompts keep their intentional pause.
- Existing Live TV/search fixes retained. No return of the old rolling DVR buffer.

Amazon APK and Google Play AAB are available in the workflow’s store-submission-candidates artifact. They are preparation builds, not a claim of store acceptance. Store listing artwork, privacy/data declarations, account setup and physical-device checks still require completion.
