# Zako Segmented Live Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Zako's fragile single-file temporary DVR with a bounded segmented rolling live engine that survives weak provider feeds without loops, preserves live playback, and reduces Fire TV memory pressure.

**Architecture:** Keep exactly one provider ingest connection. Write packet-aligned MPEG-TS data into small sequential disk segments and expose a monotonic virtual byte address space to the localhost timeshift reader. Retire old segments only after the reader has moved beyond them, so the DVR window rolls continuously instead of hitting the 1 GB/3.5 GB single-file cap. Keep long buffering on disk rather than RAM and preserve existing UI/remote behavior.

**Tech Stack:** Android/Kotlin, Media3 1.9.0, OkHttp 4.12, MPEG-TS 188-byte packets, localhost HTTP timeshift server, existing Storage/Recording/StabilityCore infrastructure.

**Spec:** Research/design agreed in the Zako project conversation on 2026-09-13.

## Global Constraints
- Fire TV target remains approximately 1 GB RAM.
- No server/CDN/backend dependency.
- One provider connection per live channel.
- Preserve existing Zako UI, guide, D-pad controls, downloads, recordings and Simple Mode.
- Never create a >4 GiB temporary DVR file; USB may be FAT32-like.
- Temp DVR remains bounded and deletes oldest data instead of stopping or jumping to the beginning.
- Long cushion/history belongs on disk, not a giant heap allocation.
- Do not publish an updater until verification, compile and permanent signing all pass.

---

### Task 1: Segmented ring model

**Files:**
- Create through release patch: `app/src/main/java/com/easyiptv/player/TimeshiftRing.kt`
- Create verifier: `tools/verify_v439.py`

**Interfaces:**
- Produces `TimeshiftSegment(id: Long, file: File, virtualStart: Long, length: Long)`.
- Produces `TimeshiftRing.append(packetBytes)`, `snapshot()`, `resolve(virtualOffset)`, `oldestVirtualOffset`, `newestVirtualOffset`, `close()`.

- [ ] Write verifier assertions first for segment-size bound, 188-byte alignment, monotonic virtual offsets and retirement of oldest completed segments.
- [ ] Run verifier against the current generated source and require RED because `TimeshiftRing` does not exist.
- [ ] Implement the minimal synchronized ring with completed immutable segments plus one active segment.
- [ ] Set a conservative segment target around 8 MiB and align rollover to 188 bytes.
- [ ] Keep a bounded retained-byte budget derived from the existing storage policy; delete only completed segments.
- [ ] Run verifier and require GREEN.
- [ ] Commit.

### Task 2: One-connection provider writer

**Files:**
- Modify generated `MainActivity.kt` Timeshift writer through the new release patch.
- Modify `TimeshiftRing.kt`.

**Interfaces:**
- Consumes the existing bounded OkHttp live client/reconnect policy.
- Produces a single writer feeding `TimeshiftRing` and live telemetry `bytesWritten`, `throughputBps`, `lastDataAt`.

- [ ] Add failing verifier assertions proving the old append-only `timeshift.ts` safety-cap stop is gone from the active writer path.
- [ ] Run RED.
- [ ] Replace single-file writes with packet-aligned ring appends while retaining exactly one provider request at a time.
- [ ] Measure useful incoming bytes over monotonic time and update a smoothed throughput estimate without retaining samples in an unbounded collection.
- [ ] Preserve 12-second stalled-read timeout and bounded reconnect backoff.
- [ ] Run GREEN and commit.

### Task 3: Virtual-offset localhost reader

**Files:**
- Modify TimeshiftServer code in generated `MainActivity.kt` through release patch.
- Modify `TimeshiftRing.kt`.

**Interfaces:**
- Consumes `resolve(virtualOffset)` and ring snapshots.
- Produces HTTP playback that crosses segment boundaries without resetting Media3 position.

- [ ] Add failing tests/verifier checks for resolving an offset in segment N, advancing into N+1, and clamping a request older than the retained window.
- [ ] Run RED.
- [ ] Replace physical-file byte offsets with monotonic virtual offsets.
- [ ] On segment EOF, atomically resolve the next segment and continue the same HTTP response.
- [ ] If requested history was retired, clamp to oldest retained packet/PAT-safe point rather than byte zero.
- [ ] Keep the reader following the active segment while the writer grows it.
- [ ] Run GREEN and commit.

### Task 4: DVR timeline and weak-feed recovery

**Files:**
- Modify live governor/player logic in generated `MainActivity.kt` through release patch.

**Interfaces:**
- Consumes oldest/newest virtual offsets, throughput and last-data timestamp.
- Produces stable live-edge, pause, rewind and FF behavior without replaying a stale one-minute loop.

- [ ] Add failing checks that the old `Timeshift.hitCap -> zapTo()` rollover path is absent.
- [ ] Run RED.
- [ ] Derive live cushion from virtual writer/read positions rather than a stopped file length.
- [ ] When provider data stalls, keep playing retained completed data until cushion is exhausted; never seek to the beginning automatically.
- [ ] When the viewer FFs to live, clamp to a safe distance behind the actively written tail so Media3 does not repeatedly collide with an incomplete TS packet.
- [ ] Keep playback speed at 1.0x.
- [ ] Record bounded StabilityCore events for ring rollover, behind-window clamp, provider stall and reconnect.
- [ ] Run GREEN and commit.

### Task 5: Recording tee across segments

**Files:**
- Modify Recording code in generated source through release patch.
- Modify `TimeshiftRing.kt` only if a read-cursor helper is required.

**Interfaces:**
- Consumes a virtual read cursor independent from the playback cursor.
- Produces a normal recording file without opening a second provider stream.

- [ ] Add failing checks proving recording no longer assumes one `timeshift.ts` file/physical `bytesWritten` cursor.
- [ ] Run RED.
- [ ] Give recording its own virtual cursor and copy sequential bytes across completed/active ring segments.
- [ ] Prevent retirement of a completed segment until active playback/recording readers have advanced beyond it, with a hard bounded fallback to protect storage.
- [ ] Preserve existing recording completion/keep-delete behavior.
- [ ] Run GREEN and commit.

### Task 6: Low-RAM hardening

**Files:**
- Modify player/load-control and release build configuration through patch.

**Interfaces:**
- Keeps Media3 heap buffer bounded; disk ring supplies long history.

- [ ] Add failing checks for explicit low-RAM buffer byte cap and no unbounded in-memory segment payload list.
- [ ] Run RED.
- [ ] Retain a fixed Media3 target-buffer cap on low-RAM Fire TV rather than increasing heap buffer to solve weak feeds.
- [ ] Ensure ring snapshots contain metadata only; stream bytes directly to disk.
- [ ] Cancel obsolete writer/reader jobs on zap, Stop, trim-memory and Activity destruction.
- [ ] Evaluate R8/resource shrinking in release CI; enable only if the signed release smoke/build checks stay green.
- [ ] Run GREEN and commit.

### Task 7: Release gate

**Files:**
- Create `.github/workflows/release439.yml` after implementation tests are green.

**Interfaces:**
- Produces signed APK/source artifact and updater only after all gates pass.

- [ ] Generate source from the full historical patch chain through 4.38.
- [ ] Run the new verifier RED before the new patch and GREEN afterward.
- [ ] Compile release APK with Java 17/Gradle 8.7.
- [ ] Verify package identity and permanent signing certificate.
- [ ] Package generated source for review.
- [ ] Publish the new release only after every previous step succeeds.
- [ ] Update `latest.json` only after release assets exist.
- [ ] Re-read `main/latest.json` after all racing workflows settle and repair it if an older workflow overwrites the version.
- [ ] Commit/release only with fresh green evidence.

## Self-review
- Covers the observed one-minute weak-channel loop, the ~45-minute cap behavior, weak-feed stalls, FF-to-live instability, one-stream constraint, recording tee, FAT32-size safety and low-RAM requirement.
- Does not redesign UI or add an on-device LLM.
- Uses virtual offsets consistently for playback and recording.
- Keeps HLS/provider-protocol experimentation separate from the ring-buffer correctness work so transport tuning cannot destabilize DVR rollover.
