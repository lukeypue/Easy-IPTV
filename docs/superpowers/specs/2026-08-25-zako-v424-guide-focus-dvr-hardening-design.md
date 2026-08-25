# Zako v4.24 Guide, Focus, DVR, and Stability Polish Design

Date: 2026-08-25
Branch: `zako-v4.24-polish`

## Goal

Polish Zako's Movies, Series, Live TV guide, Mini Guide, Android live-DVR controls, and recording stability while preserving the app's low-memory Fire TV behavior.

## Constraints

- Fire TV remains the primary performance target.
- Remote-only D-pad navigation must be deterministic.
- No extra player, tuner, or duplicate provider stream should be created for guide or DVR UI features.
- EPG work must remain bounded in memory; keep the existing near-term in-memory guide window instead of expanding to a full week.
- Movie/series detail data should be fetched lazily only when requested.
- Do not merge to `main` during this test pass.

## 1. Movies and Series D-pad focus

The 5-column poster grid must own horizontal movement inside each row.

Expected behavior:
- Left from columns 2-5 moves to the immediately previous poster.
- Left from column 1 moves to the category rail.
- Right from columns 1-4 moves to the next poster.
- Download/info controls must not steal ordinary poster-to-poster navigation.
- Returning from playback/detail restores the previous poster and category.

Implementation direction:
- Remove or narrow the broad whole-content Left fallback that can pre-empt the grid.
- Use explicit per-card focus relationships where needed.
- Audit Movies, Series, Series Detail, Search result rows, Downloads, Recordings, and Live TV for D-pad dead ends.

Poster actions:
- The poster itself remains the horizontal grid focus target.
- On Fire TV, Down from a focused poster enters a tiny action row for that poster (`ⓘ INFO` and download where available); Left/Right moves only inside that action row, and Up returns to the poster.
- On Android touch, the Info/download controls are directly tappable.
- Hold-OK download remains supported as a shortcut.

## 2. Live category wraparound

Live channel lists should behave like a cable lineup:
- Down on the final channel wraps to the first channel.
- Up on the first channel wraps to the final channel.
- Normal Up/Down behavior remains unchanged in the middle of the list.

This should be implemented as focus/navigation behavior only; it must not preload or duplicate channel streams.

## 3. Readability and color hierarchy

Keep pink as the D-pad focus indicator. Improve information readability without turning the whole UI into one accent color.

Use:
- Primary labels/categories: bright near-white.
- Secondary instructions: brighter white than the current gray.
- Current show/program information: cyan/light blue and bold.
- Time labels: bright white.
- Current Mini Guide row: light cyan/blue highlight while retaining the pink focus border.
- Previous-channel program name: bright cyan/light blue or bright white.
- Download icon: vivid green.

Apply this particularly to:
- Movies/Series helper text.
- Mini Guide instructions.
- Mini Guide current program and times.
- Previous-channel cards.
- Live guide times/program labels.
- Category rail labels.

## 4. Enhanced three-row Mini Guide

Keep the compact three-row design. Do not convert the Mini Guide into the full program grid.

Each visible row should show:
- Channel number/NOW marker.
- Channel name.
- Current program title.
- Current program start/end time when available.

The current channel row receives a stronger light-blue/cyan background treatment.

Data behavior:
- Reuse `EpgStore` already in memory.
- Query only the three visible Mini Guide rows.
- Do not trigger a second EPG download, player, tuner, or provider connection.
- If no EPG is available for a row, show the channel cleanly without placeholder clutter.

## 5. Full Live TV guide grid

Add a lightweight X1/TiviMate-style time grid while retaining the existing per-channel expanded schedule button.

Entry point:
- Add one compact, focusable `GRID GUIDE`/guide action at the top of the Live TV pane.
- The normal channel list remains the default Live TV view.
- Back from the grid returns to the normal channel list at the same category/nearby channel position.

Layout:
- Channels vertically.
- Time horizontally.
- About a 2-hour visible time window.
- Only currently visible channel rows and visible time cells are composed.

Navigation:
- Up/Down moves between channel rows.
- Left/Right moves through program/time cells.
- Selecting a currently airing show opens a lightweight program action card with Watch, Info, and Record where applicable.
- Selecting a future show opens the same card with Info and Schedule Recording where supported.

Memory strategy:
- Reuse the current `EpgStore` data.
- Keep the existing approximately 48-hour in-memory guide horizon for this version.
- Do not materialize a giant full-grid model for all channels/times.
- Keep the existing individual-channel schedule view for longer browsing.

## 6. Movie and Series info

Add an `ⓘ INFO` action next to the poster/title actions for movies and series.

For Xtream movies:
- Fetch detailed VOD info only when the user opens Info.
- Display description/plot and optional provider-supplied metadata such as year/rating when present.
- Provide Play, Download, and Close actions.

For Series:
- Fetch/reuse series info lazily when Info is opened, without bulk-fetching all series metadata.
- Display provider-supplied series description and available metadata.

For M3U or providers that do not supply metadata:
- Show a simple `No description supplied by this playlist/provider` state.

Do not bulk-fetch descriptions for the entire catalog.

## 7. Android live-DVR controls

Use the same underlying Zako live-DVR/timeshift engine on Android touch devices rather than creating a second DVR path.

Touch behavior:
- On touch-capable Android devices, tapping live video reveals a compact touch control row.
- Controls: Rewind, Pause/Play, Fast Forward, Return to Live, Record.
- The row auto-hides after inactivity like normal playback controls.
- Fire TV remote behavior and Mini Guide controls remain unchanged.

These controls call the same `Playback.seekDvrBy`, playback, and recording functions used by the Fire TV experience.

Movies, Series, downloads, and saved recordings continue using the existing VOD/saved-media player controls.

## 8. Recording and memory hardening

The reported failure mode is: active recording -> enter Recordings -> UI stalls/focus stops -> app may exit under load.

Hardening work:
- Move recording-directory scan/sort work off the main thread.
- Reduce unnecessary refresh frequency while recording.
- Ensure entering Recordings does not create a second player or provider stream.
- During an active recording, avoid rendering an unnecessary live preview SurfaceView in the home/recordings context; show a lightweight `Recording in progress` status card instead while keeping the actual recording/DVR stream alive.
- Review lifecycle cancellation for periodic UI loops.
- Add Android memory-trim handling for disposable image/cache resources where safe.
- Preserve the existing foreground `RecordingService`, provider-stream budgeting, storage guards, and same-channel DVR tee behavior.

## 9. Verification

Before calling the change complete:

1. Build through GitHub Actions on `zako-v4.24-polish`.
2. Confirm the permanent release signing certificate still matches the established Zako certificate.
3. Verify no debug build is distributed.
4. Exercise deterministic focus cases:
   - Movies/Series row Left/Right across all five positions.
   - Column-1 Left to categories.
   - Poster Down -> action row -> Up back to poster.
   - Live list first/last wraparound.
   - Mini Guide three-row navigation.
   - Full guide Up/Down/Left/Right.
5. Exercise guide cases with and without EPG data.
6. Exercise Android live touch DVR controls.
7. Stress path: live playback + DVR Live + active recording + enter/leave Recordings repeatedly.
8. Confirm no extra provider stream is opened by guide UI or same-channel recording.

## Non-goals for this pass

- No 7-14 day guide held fully in RAM.
- No multiview.
- No additional tuner/player for guide previews.
- No Amazon Appstore work.
- No merge to `main` until physical-device testing passes.
