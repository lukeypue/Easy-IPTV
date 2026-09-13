from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')
changes = []

def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1, found {n}')
    changes.append(label)
    return text.replace(old, new, 1)

gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 62', gradle, count=1)
if n != 1: raise SystemExit('versionCode')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.37"', gradle, count=1)
if n != 1: raise SystemExit('versionName')
changes += ['versionCode 62', 'versionName 4.37']

# Bound ExoPlayer's allocator on ~1 GB Fire TV hardware. Zako's DVR writer is
# already a separate disk-backed cushion, so letting the player allocate an
# unbounded track-derived target duplicates buffering without helping recovery.
main = once(main,
'''            .setTargetBufferBytes(C.LENGTH_UNSET)\n            .setPrioritizeTimeOverSizeThresholds(true)\n''',
'''            // ZAKO_V437_FIRETV_BUFFER_BUDGET: keep Media3's in-memory side bounded.\n            // The timeshift writer remains the long cushion; Media3 only needs enough\n            // RAM to decode smoothly and bridge short provider bursts.\n            .setTargetBufferBytes(32 * 1024 * 1024)\n            .setPrioritizeTimeOverSizeThresholds(false)\n''',
'bounded Media3 allocator')

# Steady recovery used Media3 seekTo() on an unknown-length, still-growing
# progressive TS source. That is the wrong seek primitive for this DVR design.
# Reuse Zako's packet-aligned byte-offset reopen path instead.
main = once(main,
'''                                stallRestarts == 0 && !directLive && p.currentPosition > 12_000 -> {\n                                    stallRestarts = 1\n                                    bufferingSince = 0L; lastBufMs = -1L\n                                    p.seekTo((p.currentPosition - 8_000).coerceAtLeast(0))\n                                    p.play()\n                                }\n''',
'''                                stallRestarts == 0 && !directLive && p.currentPosition > 12_000 -> {\n                                    stallRestarts = 1\n                                    bufferingSince = 0L; lastBufMs = -1L\n                                    StabilityCore.note("steady_recovery packet_reopen cushion=${cushionMs}ms")\n                                    // Never ask Media3 to seek inside the unknown-length growing TS.\n                                    // Zako owns DVR seeking and reopens on a verified TS packet/PAT boundary.\n                                    if (!seekDvrBy(-8_000L)) {\n                                        StabilityCore.note("steady_recovery packet_reopen_unavailable")\n                                        zapTo(currentIdxC.intValue, preserveDirect = directLive)\n                                    }\n                                }\n''',
'steady recovery uses DVR-safe seek')

# When Media3 reports a local DVR extractor/playback failure but the provider
# writer is healthy, preserve the provider connection and reopen the localhost
# DVR near the current point. Do not throw away a recovering sports feed.
old_retry = '''                        if (liveMode) {\n                            // Timeshift trouble? After 3 strikes, flip to direct\n                            // provider playback so video ALWAYS works.\n                            noteLiveFail()\n                            zapTo(currentIdxC.intValue, preserveDirect = directLive)\n                        } else {\n'''
new_retry = '''                        if (liveMode) {\n                            val now = System.currentTimeMillis()\n                            val writerHealthy = !directLive && Timeshift.active &&\n                                Timeshift.bytesWritten > 188L * 50L &&\n                                (now - Timeshift.lastByteAt) < 5_000L\n                            StabilityCore.note(\n                                "live_player_error code=${error.errorCode} writerHealthy=$writerHealthy direct=$directLive " +\n                                    "bytes=${Timeshift.bytesWritten} buffered=${p.totalBufferedDuration}"\n                            )\n                            // A healthy writer proves the remote provider is still flowing.\n                            // Recover only the local extractor/player path first; restarting\n                            // the writer here caused weak/bursty feeds to lose their cushion.\n                            if (writerHealthy && canSeekDvr()) {\n                                if (!seekDvrBy(-2_000L)) {\n                                    noteLiveFail()\n                                    zapTo(currentIdxC.intValue, preserveDirect = directLive)\n                                }\n                            } else {\n                                noteLiveFail()\n                                zapTo(currentIdxC.intValue, preserveDirect = directLive)\n                            }\n                        } else {\n'''
main = once(main, old_retry, new_retry, 'preserve healthy writer on player error')

# Prime by observed throughput rather than a fixed 512 KiB alone. High bitrate
# sports streams need more bytes for the same real-time cushion; cap it to stay
# friendly to low-memory Fire TV devices.
main = once(main,
'''                val waited = android.os.SystemClock.elapsedRealtime() - started\n                val ready = Timeshift.bytesWritten >= DVR_PRIME_BYTES || waited >= 2_000L\n                if (ready) {\n''',
'''                val waited = android.os.SystemClock.elapsedRealtime() - started\n                val observedBps = Timeshift.throughputBps.coerceAtLeast(0.0)\n                val throughputPrime = if (observedBps > 0.0)\n                    (observedBps * 2.5).toLong().coerceIn(DVR_PRIME_BYTES, 3L * 1024L * 1024L)\n                else DVR_PRIME_BYTES\n                val ready = Timeshift.bytesWritten >= throughputPrime || waited >= 3_500L\n                if (ready) {\n                    StabilityCore.note("dvr_prime bytes=${Timeshift.bytesWritten} target=$throughputPrime bps=${observedBps.toLong()}")\n''',
'adaptive DVR prime')

MAIN.write_text(main, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
print('Applied Zako 4.37 buffering recovery core:')
for c in changes: print(' -', c)
