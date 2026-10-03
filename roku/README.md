# RYZOD Media Player for Roku — 0.1.0

Native BrightScript/SceneGraph port based on the latest Android/Firestick **4.70 (build 94)** source, commit `637c449f79db1f2d784d0561041a06be750c779b`. Work is isolated on `feature/ryzod-roku`. The Android and Apple versions are unaffected.

Includes the RYZOD artwork, eight matching menu sections (Live TV, Movies, Series, Search, Downloads, Recordings, Playlists, Settings), Xtream/M3U sign-in, three saved playlists, scoped favorites, category/poster browsing, seasons/episodes, literal search, live guide, picture preview, one fullscreen player with mini controls, VOD resume, audio/caption selection, and companion-backed recording/download controls. Content and guide data come from your own provider.

## Install on Roku

Use `out/RYZOD-Roku-0.1.0.zip` directly; do not unzip it before uploading. This is a developer sideload build, not a Roku Store release or APK.

1. Enable developer mode on your Roku: press **Home three times, Up twice, Right, Left, Right, Left, Right**. Follow Roku's prompts, accept the developer agreement, set a password, and restart. Note the Roku IP address shown there.
2. On a computer on the same network, open `http://ROKU-IP`, sign in as `rokudev` with that password, and upload/install the ZIP. Installing replaces the existing developer channel on that Roku.
3. Open RYZOD and enter your Xtream host/username/password or M3U URL. The three-page keyboard includes URL symbols; `*` changes pages, Replay deletes, and Back cancels.
4. Use D-pad and OK. In Live TV, Up from the first channel opens Categories / Update guide / Picture. Back returns to the menu. Movies/Series expose Categories above the first poster row and through `*`.
5. Fullscreen OK shows the mini controls. Back first hides them, then returns to the guide/preview. Live/seek/track controls use the stream's native capabilities.

For saved recordings and downloads, use `out/RYZOD-Companion-0.1.0.zip` and its README. Pair its LAN address and token in Settings. Recording confirmation preserves complete program times; manual schedules support the next seven days in five-minute increments. Stop/delete actions require confirmation.

## Platform differences

Roku has a native player and codec support, rather than Android's decoder. A stream that works on Android can require a provider-compatible HLS rendition for Roku. Settings offers HLS/TS live format. Roku's pause/seek buffer depends on the stream and device; there is no guaranteed Android-style thirty-minute buffer. Persistent DVR/download files require the optional computer/NAS companion because a Roku channel cannot write them to USB. Updates use a new sideload ZIP; Roku Store distribution and signing are separate work.

Favorites/resume/settings use a bounded Roku registry (three playlists, 150 favorites, 80 resume entries, 28 KB serialized budget). A large saved URL can exhaust that budget and displays a save error. The guide/catalog renders only visible rows/posters; XMLTV is limited to the fetched response size and may need a provider-filtered feed for large providers. Guide gaps stay selectable for manual recording.

When paired, provider playback is gated by companion reservations, renewed every three seconds; expiry or an upcoming recording stops Roku playback to protect the configured stream limit. Losing the companion blocks provider playback until connectivity is restored or pairing is cleared. Saved local media does not use a provider reservation. Stream usage by other apps/devices is outside this coordination.

## Build and verify

Requires Node 24, Python 3.10+, and FFmpeg/ffprobe for recording verification:

```sh
cd roku
npm ci
npm run verify
```

This runs the BrightScript behavior suite, native SceneGraph simulator UI regressions, threaded Task-worker HTTP integration, companion HTTP/persistence/download/FFmpeg tests, compiler, asset/XML checks, and deterministic package generation. SHA-256 checksums are written to `out/SHA256SUMS.txt`. The scoped Roku workflow uploads both packages and test instructions; it does not publish an Android update.

Read TEST_REPORT.md for what has been exercised and the required physical-device acceptance checks. This build has not been played on a physical Roku in this workspace.
