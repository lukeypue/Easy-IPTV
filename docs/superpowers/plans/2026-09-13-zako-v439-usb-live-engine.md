# Zako 4.39 USB Live Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragile single-file DVR/timeshift path with a low-memory segmented rolling engine that prefers writable USB storage, keeps Smooth/direct playback intact as the safety path, and uses one provider connection for live viewing plus same-channel DVR/recording.

**Architecture:** The provider ingest job owns one upstream connection and writes MPEG-TS packets into a disk-backed `TimeshiftRing`. Playback and same-channel recording read a monotonic virtual timeline that spans small physical segment files. A storage policy chooses writable USB first, internal app storage second, and Smooth/direct playback when safe local buffering is unavailable.

**Tech Stack:** Android/Kotlin, Jetpack Compose, Media3/ExoPlayer, OkHttp, app-private/external storage, Python source-patch/verifier scripts, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-13-usb-live-dvr-engine-design.md`

## Global Constraints

- Fire TV target remains low-RAM (~1 GB class); disk-first buffering and bounded RAM are mandatory.
- Smooth/direct playback remains a known-good fallback and must not depend on the DVR ring.
- No guessed `.ts` to `.m3u8` conversion during channel startup.
- Media3 error callbacks must never synchronously rebuild the full player/channel stack.
- Live + local DVR for the watched channel must consume one provider stream connection.
- Writable USB is preferred automatically; internal app storage is fallback; customers are not required to own USB.
- Existing guide, remote-control behavior, downloads, recordings, and UI styling are preserved unless correctness requires a change.
- No server/cloud backend and no heavyweight on-device AI.

---

### Task 1: Make Steady buffer configuration impossible to crash Media3

**Files:**
- Create: `tools/apply_v439.py`
- Create: `tools/verify_v439.py`
- Modify through patch: `app/src/main/java/com/easyiptv/player/MainActivity.kt`

**Interfaces:**
- Consumes: existing `live_steady_recovery` preference, live buffer setting, Media3 `DefaultLoadControl.Builder`.
- Produces: `safeLiveBufferConfig(...)` logic where `bufferForPlaybackAfterRebufferMs <= minBufferMs` for every Small/Normal/Big × Smooth/Steady combination.

- [ ] **Step 1: Write the failing verifier**

Create `tools/verify_v439.py` so it fails unless generated source contains `ZAKO_V439_SAFE_BUFFER_INVARIANTS`, contains `safeRebufferMs = minOf(requestedRebufferMs, minBufferMs)`, and no Steady path can assign a rebuffer value greater than minimum buffer.

```python
required = [
    'ZAKO_V439_SAFE_BUFFER_INVARIANTS',
    'safeRebufferMs = minOf(requestedRebufferMs, minBufferMs)',
]
for marker in required:
    assert marker in main_activity, marker
```

- [ ] **Step 2: Run verifier before patch and require RED**

Run generated v4.38 source, then:

```bash
python3 tools/verify_v439.py
```

Expected: FAIL because the v4.39 marker and safe clamp do not exist yet.

- [ ] **Step 3: Implement the smallest safe buffer policy**

Patch the load-control construction so Steady may request a larger cushion but the value passed to Media3 is always valid:

```kotlin
val requestedRebufferMs = if (steadyRecovery) steadyRebufferMs else normalRebufferMs
val safeRebufferMs = minOf(requestedRebufferMs, minBufferMs)
```

Add a `StabilityCore.note(...)` entry with min/rebuffer values before player creation so future failures are diagnosable.

- [ ] **Step 4: Run verifier after patch and require GREEN**

```bash
python3 tools/apply_v439.py
python3 tools/verify_v439.py
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tools/apply_v439.py tools/verify_v439.py
git commit -m "fix: enforce safe live buffer invariants"
```

---

### Task 2: Introduce the segmented `TimeshiftRing`

**Files:**
- Create through `tools/apply_v439.py`: `app/src/main/java/com/easyiptv/player/TimeshiftRing.kt`
- Extend: `tools/verify_v439.py`

**Interfaces:**
- Produces: `TimeshiftRing.open(root: File, historyMs: Long)`, `append(packetBytes: ByteArray, length: Int)`, `snapshot(): RingSnapshot`, `openReader(virtualOffset: Long): RingReader`, `close()`.
- Segment metadata: id, file, virtualStartByte, virtualEndByte, openedElapsedMs, closedElapsedMs, byteLength, finalized, activeReaders.

- [ ] **Step 1: Add RED checks for ring structure**

Verifier must require `TimeshiftRing.kt`, 188-byte alignment, monotonic virtual offsets, and deferred deletion while `activeReaders > 0`.

```python
assert 'TS_PACKET_BYTES = 188' in ring
assert 'virtualStartByte' in ring and 'virtualEndByte' in ring
assert 'activeReaders' in ring
```

- [ ] **Step 2: Generate source and confirm RED**

```bash
python3 tools/verify_v439.py
```

Expected: FAIL because `TimeshiftRing.kt` does not exist.

- [ ] **Step 3: Implement bounded segment rotation**

Use whole MPEG-TS packets only. Rotate at a bounded target such as 4 MiB aligned down to 188 bytes:

```kotlin
private const val TS_PACKET_BYTES = 188
private const val TARGET_SEGMENT_BYTES = 4L * 1024L * 1024L
private val alignedTargetBytes = TARGET_SEGMENT_BYTES - (TARGET_SEGMENT_BYTES % TS_PACKET_BYTES)
```

Maintain a monotonic virtual byte counter independent of physical file deletion.

- [ ] **Step 4: Implement retention and reader protection**

Delete only finalized segments outside the configured history window and only when `activeReaders == 0`. Mark otherwise-eligible segments for deferred reclaim.

- [ ] **Step 5: Verify GREEN and commit**

```bash
python3 tools/apply_v439.py
python3 tools/verify_v439.py
git add tools/apply_v439.py tools/verify_v439.py
git commit -m "feat: add segmented timeshift ring"
```

---

### Task 3: Replace the single-file tail reader with a virtual-timeline reader

**Files:**
- Modify through patch: `app/src/main/java/com/easyiptv/player/MainActivity.kt`
- Modify through patch: `app/src/main/java/com/easyiptv/player/TimeshiftRing.kt`
- Extend: `tools/verify_v439.py`

**Interfaces:**
- Consumes: `TimeshiftRing.openReader(virtualOffset)`.
- Produces: local playback stream that walks segment boundaries without exposing one ever-growing unknown-length file.

- [ ] **Step 1: Add RED verifier checks**

Require the local server/reader to map virtual offsets through `TimeshiftRing`, and reject direct dependence on a single `timeshift.ts` file for the v4.39 DVR path.

- [ ] **Step 2: Confirm RED**

```bash
python3 tools/verify_v439.py
```

Expected: FAIL while `TimeshiftServer` still reads the single file.

- [ ] **Step 3: Implement reader mapping**

At each segment boundary, close the old file handle, decrement its reader count, acquire the next segment, and continue from its first packet. If the requested history has already been reclaimed, clamp to `oldestVirtualByte` and log `ring_seek_clamped_oldest`.

- [ ] **Step 4: Preserve stable live-tail behavior**

When a reader reaches the writer tail, wait for new finalized/readable bytes instead of recursively rebuilding Media3. If no local data remains after bounded recovery, return control to the existing Smooth fallback policy.

- [ ] **Step 5: Verify and commit**

```bash
python3 tools/apply_v439.py
python3 tools/verify_v439.py
git add tools/apply_v439.py tools/verify_v439.py
git commit -m "feat: read DVR over virtual segment timeline"
```

---

### Task 4: Put same-channel recording on the ring

**Files:**
- Modify through patch: `app/src/main/java/com/easyiptv/player/MainActivity.kt`
- Extend: `tools/verify_v439.py`

**Interfaces:**
- Consumes: finalized ring segments / `RingReader` from a selected virtual start point.
- Produces: same-channel recording without opening another provider connection.

- [ ] **Step 1: Add RED checks**

Verifier requires marker `ZAKO_V439_RECORD_FROM_RING` and rejects a second same-channel provider open when the live ingest owns that channel.

- [ ] **Step 2: Confirm RED**

Run verifier and require failure.

- [ ] **Step 3: Implement recording cursor**

Create a recording cursor from the current or requested virtual point and copy finalized MPEG-TS packets into the recording file. Hold/release reader references so retention never deletes a segment being copied.

- [ ] **Step 4: Keep different-channel recording behavior bounded by provider stream limit**

Do not change the existing separate-stream policy for recording a different channel.

- [ ] **Step 5: Verify and commit**

```bash
python3 tools/apply_v439.py
python3 tools/verify_v439.py
git add tools/apply_v439.py tools/verify_v439.py
git commit -m "feat: record watched channel from timeshift ring"
```

---

### Task 5: Add USB-preferred storage selection with automatic fallback

**Files:**
- Create through patch: `app/src/main/java/com/easyiptv/player/LiveStorageManager.kt`
- Modify through patch: `app/src/main/java/com/easyiptv/player/MainActivity.kt`
- Extend: `tools/verify_v439.py`

**Interfaces:**
- Produces: `LiveStorageTarget(root: File, kind: USB|INTERNAL, freeBytes: Long)` or `null`.
- Consumes: app external-files directories and internal files directory.

- [ ] **Step 1: Add RED checks**

Require `LiveStorageManager.kt`, USB-first candidate ordering, a real write/delete probe, a free-space safety floor, and internal fallback.

- [ ] **Step 2: Confirm RED**

Run verifier and require failure.

- [ ] **Step 3: Implement automatic target selection**

For each candidate, create a tiny probe file in the app-owned directory, flush it, delete it, and verify usable free space. Prefer external/USB candidates that pass; otherwise use internal storage.

- [ ] **Step 4: Handle USB disappearance**

On write failure or missing mount, stop the affected ring safely, log `usb_storage_lost`, and either reopen on internal storage for the next channel/session or use Smooth direct playback. Never crash the activity.

- [ ] **Step 5: Verify and commit**

```bash
python3 tools/apply_v439.py
python3 tools/verify_v439.py
git add tools/apply_v439.py tools/verify_v439.py
git commit -m "feat: prefer USB for live DVR storage"
```

---

### Task 6: Bound memory and add weak-channel cushion/diagnostics

**Files:**
- Modify through patch: `app/src/main/java/com/easyiptv/player/MainActivity.kt`
- Modify through patch: `app/src/main/java/com/easyiptv/player/TimeshiftRing.kt`
- Extend: `tools/verify_v439.py`

**Interfaces:**
- Consumes: device memory class, ring writer/reader state, existing `StabilityCore`.
- Produces: bounded Media3 target-buffer bytes and compact telemetry events.

- [ ] **Step 1: Add RED checks**

Require a bounded `targetBufferBytes`, fixed-size I/O buffers, no unbounded in-memory segment list, and telemetry markers for stalls/reconnects/playback lag/segment age.

- [ ] **Step 2: Confirm RED**

Run verifier and require failure.

- [ ] **Step 3: Implement memory-class-based cap**

Use a small fixed policy for low-memory devices, for example 8 MiB on very low memory and up to 24 MiB on larger devices; never use an unbounded target in v4.39.

- [ ] **Step 4: Implement adaptive local cushion without playback-speed tricks**

Maintain a small lag behind writer based on recent stalls/reconnects. Do not set 0.95x or other custom playback speed. Log changes rather than constantly rebuilding the player.

- [ ] **Step 5: Verify and commit**

```bash
python3 tools/apply_v439.py
python3 tools/verify_v439.py
git add tools/apply_v439.py tools/verify_v439.py
git commit -m "feat: bound live memory and add DVR diagnostics"
```

---

### Task 7: Build, sign, and publish Zako 4.39 safely

**Files:**
- Create: `.github/workflows/release439.yml`
- Modify: `latest.json` only after successful signed release publication
- Extend: `tools/verify_v439.py`

**Interfaces:**
- Consumes: complete v4.38 patch chain plus `apply_v439.py`.
- Produces: signed `Zako-v4.39.apk`, source ZIP, release `v4.39`, updater metadata versionCode 64/versionName 4.39.

- [ ] **Step 1: Create workflow with RED -> GREEN verification**

Workflow order:

```bash
# generate and verify 4.38
python3 tools/verify_v439.py && exit 1 || true
python3 tools/apply_v439.py
python3 tools/verify_v439.py
```

The pre-patch verifier must fail; the post-patch verifier must pass.

- [ ] **Step 2: Compile release APK**

Run Gradle release build with Java 17 / Gradle 8.7 using the existing permanent signing secret flow.

- [ ] **Step 3: Verify identity and signing**

Require package/version identity and signing certificate SHA-256:

```text
8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165
```

- [ ] **Step 4: Publish release assets before updater change**

Publish `Zako-v4.39.apk` and `Zako-v4.39-source.zip` to tag `v4.39` only after all prior checks pass.

- [ ] **Step 5: Point updater to 4.39 and verify no older workflow overwrote it**

Expected `latest.json`:

```json
{
  "versionCode": 64,
  "versionName": "4.39",
  "downloadUrl": "https://github.com/lukeypue/Easy-IPTV/releases/download/v4.39/Zako-v4.39.apk"
}
```

- [ ] **Step 6: Final verification**

Confirm workflow conclusion success, release assets exist, signing passed, source verifier passed, and `main/latest.json` still points to 4.39 after any racing legacy workflow completes.

- [ ] **Step 7: Commit workflow**

```bash
git add .github/workflows/release439.yml
git commit -m "ci: build and publish Zako 4.39 USB live engine"
```

---

## Acceptance Test on Fire TV

After CI is green, the human test sequence is:

1. Smooth + DVR off: channel plays as the reference path with no regression.
2. Steady on: selecting a channel does not exit the app.
3. DVR on: live channel plays while ring grows on USB when available.
4. Leave one channel running beyond 45 minutes: timeshift continues rolling rather than hitting the old file cap.
5. Rewind, FF, pause, and return to live across multiple segment boundaries.
6. Record the currently watched channel and verify it does not consume an extra provider connection.
7. Remove/unmount USB during a controlled test: app remains alive and falls back safely.
8. Stress with recording/download activity on the low-RAM Fire Stick and verify no app exit.
