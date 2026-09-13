# Zako USB-Backed Live/DVR Engine Design

## Goal
Build a resilient low-memory live-TV and DVR engine for Fire TV that preserves the current Smooth/direct playback path as a known-good fallback while moving heavy timeshift storage to USB when available.

## Core Architecture
Zako will have two clearly separated live-playback paths:

1. **Smooth direct playback** remains the safety/reference path. It plays the provider stream directly and does not depend on the DVR engine. If the DVR path is unavailable or unhealthy, Zako can fall back to Smooth without rebuilding the rest of the app.
2. **Buffered DVR playback** uses one provider connection, writes the incoming transport stream into small disk-backed segments, and serves those segments to the player through a stable local reader. The preferred storage target is writable USB; internal app storage is the fallback.

The USB drive acts as a local storage appliance, not as a network cloud. It holds timeshift segments, recordings, downloads, indexes, and bounded diagnostics. RAM is used only for small I/O buffers and short queues.

## Storage Selection
At startup and when entering live TV, a storage manager will identify the best writable target:

- Preferred: approved writable USB/external app storage with sufficient free space.
- Fallback: internal app storage.
- Last resort: Smooth direct playback with DVR disabled if neither location can safely hold a rolling buffer.

The app must not require the customer to understand file paths. USB detection and selection should be automatic after a successful write test.

## Segmented Rolling Timeshift
The current single growing `timeshift.ts` file will be replaced by a segmented rolling ring.

Each segment stores whole MPEG-TS packets and metadata including:

- segment id
- file path
- virtual start/end byte offsets
- segment open/close timestamps
- byte length
- whether the segment is finalized

The writer maintains a monotonic virtual byte address across segments so playback and recording can move through a continuous timeline even when old physical files are deleted.

Old segments are removed only after they fall outside the configured DVR history window and are no longer being read. This removes the current single-file cap and avoids the ~45-minute stop/fallback behavior caused by the existing 1 GB / 3.5 GB file ceilings.

## Reader and Playback Model
The player will not depend on the unstable tail of one ever-growing file. A local reader maps a virtual offset to the correct segment and streams sequentially across segment boundaries.

For weak channels, playback should stay behind the writer by a small adaptive safety cushion. The player can then consume finalized/stable data instead of repeatedly colliding with an upstream stall.

If the provider stalls:

- the writer retries with bounded backoff
- existing local buffered data remains playable
- the player is not rebuilt recursively from inside a Media3 error callback
- if the local buffer is exhausted, Zako can attempt a controlled recovery or fall back to Smooth direct playback

## One Provider Connection
Live viewing plus local timeshift must use one provider stream connection. The ingest job owns the provider connection; playback and recording consume the local segmented ring.

This protects users with low stream-count subscriptions and prevents DVR from doubling provider usage.

## Recording Integration
Recordings should consume the same segmented ring rather than opening a second provider stream when recording the currently watched channel.

A recording cursor tracks the virtual timeline and copies finalized packets/segments into the recording target. If the user started recording after joining the channel, the recording begins at the selected logical point without disturbing live playback.

Different-channel recordings can continue to use separate provider streams subject to the configured provider stream limit.

## Smooth and Steady Behavior
The current Smooth/direct path remains unchanged as much as possible because user testing shows it performs well when DVR is off.

The old Steady mode should no longer be a second fragile playback stack. Its intent becomes an adaptive buffered mode that controls how much safe local cushion to maintain. All buffer values must satisfy Media3 invariants, and every supported UI setting combination must be verified in tests.

No guessed `.ts` -> `.m3u8` URL conversion should occur during channel startup. HLS vs MPEG-TS experiments belong in a separate protocol-selection layer and may never be allowed to crash the core player.

## Memory Budget
The Fire TV target is a low-RAM device (~1 GB class), so the design must remain disk-first:

- reuse fixed I/O buffers, approximately 64 KB each
- no giant in-memory segment queue
- bounded Media3 target buffer bytes chosen from device memory class
- one shared OkHttp client/network stack
- stale writer/reader/player jobs cancelled on channel change
- no unbounded telemetry arrays or catalog retention in RAM

The storage ring may be large on USB, but the working set in memory must remain small and predictable.

## USB Capacity Policy
The 128 GB USB is useful because it lets Zako keep a much larger local media workspace without consuming internal Fire TV storage.

Initial policy:

- reserve a safety floor so Zako never fills the drive
- live timeshift uses a configured time window, not “all free space”
- recordings and downloads remain separate from rolling timeshift
- timeshift segments are disposable and are the first content reclaimed
- metadata survives app restarts only where useful; stale live segments are cleared safely

Customers without USB still get Zako. They receive a smaller internal timeshift window or Smooth direct playback automatically.

## Diagnostics / Learning Data
Zako should record compact technical telemetry that can be exported with the existing Upload Data flow:

- channel id/provider key (non-secret representation)
- protocol used
- startup time
- bytes/sec estimate
- stall count
- reconnect count
- playback lag behind writer
- segment count and oldest/newest ages
- player error code/cause
- whether recovery succeeded

This is not an on-device LLM. It is a lightweight technical learning layer so future releases can tune protocol and buffer heuristics from real user behavior.

## Failure Handling
The engine must fail closed toward a known-good playback path:

- USB disappears or becomes unwritable -> stop using it, preserve app stability, fall back to internal storage or Smooth
- local ring cannot allocate safely -> Smooth direct playback
- writer stalls -> keep serving existing buffered segments until exhausted
- player reaches deleted history -> seek to oldest available segment with a visible/logged boundary, not a crash
- player reaches live edge -> continue from newest safe segment or transition to direct/live policy
- Media3 error callbacks must never recursively invoke a full channel rebuild synchronously

## Testing Strategy
Implementation follows TDD. Tests must cover at minimum:

1. segment rotation and packet alignment
2. virtual offset mapping across segment boundaries
3. deletion of old segments without deleting an actively read segment
4. rolling beyond the previous 1 GB / 3.5 GB single-file caps
5. USB removal/unwritable fallback
6. internal-storage fallback
7. one-provider-connection behavior for live + timeshift
8. same-channel recording from the ring
9. every Smooth/Steady/Small/Normal/Big buffer combination satisfying Media3 constraints
10. weak-channel writer stalls while buffered playback continues
11. channel changes cancel stale jobs/readers/writers
12. no synchronous recursive player rebuild from `onPlayerError`
13. bounded memory configuration for low-RAM devices

## Release Strategy
This architecture should be introduced in small verified stages rather than one unreviewable rewrite:

- **Stage A:** fix remaining Steady configuration crash and add invariant tests.
- **Stage B:** introduce `TimeshiftRing` and segmented writer/reader behind the existing live UI.
- **Stage C:** switch same-channel DVR/recording to the ring.
- **Stage D:** add USB-preferred storage manager and fallback policy.
- **Stage E:** add adaptive cushion/health scoring and compact exported diagnostics.

At every stage, Smooth direct playback remains available as the fallback and existing guide, UI, recordings, downloads, and remote-control behavior are preserved unless a change is required for correctness.

## Non-Goals
- No cloud backend or paid server infrastructure.
- No requirement that every customer buy a USB drive.
- No on-device heavyweight AI model.
- No UI redesign as part of this engine work.
- No copying proprietary Comcast/X1 code.
- No dependence on multiple provider streams for one watched channel plus its local DVR buffer.
