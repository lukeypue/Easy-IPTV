from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')
changes = []

gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 64', gradle, count=1)
if n != 1:
    raise SystemExit('versionCode bump failed')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.39"', gradle, count=1)
if n != 1:
    raise SystemExit('versionName bump failed')
changes += ['versionCode 64', 'versionName 4.39']

old = '''        val steadyRecovery = prefs.getBoolean("live_steady_recovery", false)
        val steadyStartMs = if (steadyRecovery) maxOf(lockMs, 6_000) else lockMs
        val steadyRebufferMs = if (steadyRecovery) maxOf(lockMs * 3, 12_000).coerceAtMost(20_000) else lockMs
        // ZAKO_V433_BUFFER_CAP: keep the proven startup/rebuffer cushion, but stop
        // VOD or DVR-behind-live from reserving a 60-90s unbounded sample buffer.
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val lowRam = am?.isLowRamDevice == true || (am?.memoryClass ?: 512) <= 256
        val maxBufferMs = (bufferSec * 1000 * 3).coerceIn(30_000, 60_000)
        val targetBufferBytes = if (lowRam) 32 * 1024 * 1024 else C.LENGTH_UNSET
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                (bufferSec * 1000).coerceAtMost(60_000),
                maxBufferMs,
                steadyStartMs,                             // steady mode banks a real reserve before picture
                steadyRebufferMs                              // and rebuilds more deeply after a weak-feed stall
            )
'''

new = '''        val steadyRecovery = prefs.getBoolean("live_steady_recovery", false)
        // ZAKO_V439_SAFE_BUFFER_INVARIANTS: Media3 requires both playback thresholds
        // to be <= minBufferMs. Steady may request a deeper recovery cushion, but
        // the value passed into DefaultLoadControl is clamped before player creation.
        val minBufferMs = (bufferSec * 1000).coerceAtMost(60_000)
        val requestedStartMs = if (steadyRecovery) maxOf(lockMs, 6_000) else lockMs
        val requestedRebufferMs = if (steadyRecovery) maxOf(lockMs * 3, 12_000).coerceAtMost(20_000) else lockMs
        val safeStartMs = minOf(requestedStartMs, minBufferMs)
        val safeRebufferMs = minOf(requestedRebufferMs, minBufferMs)
        StabilityCore.note("v439_buffer_policy steady=$steadyRecovery min=$minBufferMs start=$safeStartMs rebuffer=$safeRebufferMs")
        // ZAKO_V433_BUFFER_CAP: keep the proven startup/rebuffer cushion, but stop
        // VOD or DVR-behind-live from reserving a 60-90s unbounded sample buffer.
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val lowRam = am?.isLowRamDevice == true || (am?.memoryClass ?: 512) <= 256
        val maxBufferMs = (bufferSec * 1000 * 3).coerceIn(30_000, 60_000)
        val targetBufferBytes = if (lowRam) 32 * 1024 * 1024 else C.LENGTH_UNSET
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                safeStartMs,                               // steady mode banks only a valid reserve before picture
                safeRebufferMs                             // recovery cushion is guaranteed <= minBufferMs
            )
'''

count = main.count(old)
if count != 1:
    raise SystemExit(f'buffer block expected 1, found {count}')
main = main.replace(old, new, 1)
changes.append('safe Media3 buffer invariants')

MAIN.write_text(main, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
print('Applied Zako 4.39 Stage A:')
for change in changes:
    print(' -', change)
