# Zako 4.47 Full Redesign Design

## Goal
Ship one complete fresh-install signed APK for Fire TV and Android that aggressively improves Zako while protecting the live-playback behavior already proven stable.

## Non-negotiable requirements
- Complete standalone APK; fresh install must not depend on an older Zako installation.
- Preserve package com.easyiptv.player and permanent release signing identity.
- Target low-memory Fire TV hardware (~1 GB RAM) and Android phones.
- Live playback gets first priority over catalog work, downloads, artwork, search indexing, recording UI, and animation.
- Preserve the complete verified 4.24-4.46 feature chain rather than patching the raw old source in isolation.
- Remote-first D-pad operation: LEFT/RIGHT/UP/DOWN/OK/BACK, channel up/down and media keys must remain useful without a pointer.
- Pink focus language remains consistent; important program/search text may use bright yellow for separation and readability.

## Experience architecture
### TV shell
A single predictable shell owns top-level destinations: Live, Guide, Movies, Series, Search, Recordings/Downloads and Settings. Focus transitions are explicit rather than relying on Compose defaults. LEFT walks outward from content to categories to main navigation; RIGHT returns inward. BACK follows the same hierarchy before exiting playback/app.

### Live and Mini Guide
Live video remains visually dominant. The Mini Guide is compact and shows three browseable channel rows around the current selection, channel identity, current EPG title and progress. OK tunes. Previous-channel access and record/pause/transport actions are reachable without covering half the picture. Media keys work without first focusing a scrub bar. DVR/timeshift transport uses progressive seek-speed behavior while ordinary live playback remains protected.

### Startup and catalog
Startup is a state machine. During the short critical playlist/bootstrap phase all navigation is gated behind a polished loading surface so partially initialized screens cannot be entered. After the critical live/navigation model is ready, the app becomes usable and VOD catalog/index/artwork work continues incrementally at lower priority. Search is debounced and searches local indexed/cached data rather than refetching the provider for every typed character.

### Movies and Series
Lists are paged/lazy and retain only a bounded working set. Details show description/episode information when supplied by the provider. Playback is not started until required metadata is ready. Artwork caches are bounded and low-memory aware.

### Playback priority and resource budget
Keep the proven Media3 live engine and existing buffer policy unless a failing regression test/device evidence justifies changing it. Introduce a small resource policy that pauses/throttles catalog prefetch, artwork and download concurrency when live playback/DVR is active or the device reports memory pressure. Never add a second live player just to render navigation UI.

### DVR, recordings and downloads
Keep recording/download/storage engines separate from Compose UI. Preserve resumable .part downloads and Range/206 validation. Recording and download queues expose explicit states and recover after process interruption. Respect provider stream limits and explain conflicts for one-stream providers. USB storage remains optional; internal-storage/phone behavior must work without USB.

### 3D/SBS compatibility
Retain the per-channel side-by-side-to-2D compatibility path for channels that incorrectly arrive as SBS/3D. It must be opt-in per channel and must not alter normal playback.

### Updates and fresh installs
Settings always exposes Check for updates. Native updater keeps package/version/signature validation. Release 4.47 also publishes a normal full APK suitable for Downloader and fresh installation after uninstalling the old app.

## Code structure direction
MainActivity should become an activity/composition root rather than the home of every screen. New focused units should own shell/navigation, startup, live overlays, catalog/search and resource policy. Existing Data.kt, Downloads.kt, Recording.kt, Storage.kt and provider-stream logic remain independent and are refactored only where tests demonstrate a benefit.

## Verification gates
1. RED tests/verifiers describe each new 4.47 behavior before production patching.
2. Regenerate the complete 4.46 generated source and verify all legacy gates.
3. Apply 4.47 and pass its verifier plus legacy invariants that are version-independent.
4. Compile release with Java 17/Gradle 8.7.
5. Verify package com.easyiptv.player, versionCode 72, versionName 4.47 and permanent certificate SHA-256 8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165.
6. Package generated source for Claude review.
7. Publish full APK and source ZIP only after all CI gates are green.
