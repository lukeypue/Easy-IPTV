#!/usr/bin/env python3
"""Focused performance patch over the locked, fully generated 4.66 baseline."""
from pathlib import Path
import shutil
import subprocess

base = Path('app/src/main/java/com/easyiptv/player')
p = base / 'MainActivity.kt'
s = p.read_text()

def once(old, new):
    global s
    if s.count(old) != 1:
        raise SystemExit(f'Expected one 4.67 patch target, got {s.count(old)}: {old[:100]}')
    s = s.replace(old, new, 1)

a = s.index('internal object Timeshift {')
b = s.index('// ZAKO_V440_RING_SERVER:', a)
fragment = Path('tools/v467/Timeshift.kt.fragment').read_text()
fragment = fragment.replace('/* INGEST_PACKETS */', Path('tools/v467/IngestPackets.kt.fragment').read_text())
s = s[:a] + fragment + '\n\n' + s[b:]

# Local HTTP clients belong to one channel generation. Retired connections end
# promptly even while a new channel's writer is active.
once('    private var server: java.net.ServerSocket? = null', '''    @Volatile private var server: java.net.ServerSocket? = null
    private val clients = java.util.concurrent.ConcurrentHashMap.newKeySet<java.net.Socket>()
    private val handlers = java.util.concurrent.ThreadPoolExecutor(
        0, 2, 30L, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.SynchronousQueue<Runnable>(),
        java.util.concurrent.ThreadFactory { r -> Thread(r, "ryzod-dvr-reader").apply { isDaemon = true } }
    )''')
once('                    Thread { handle(sock) }.apply { isDaemon = true }.start()', '''                    clients.add(sock)
                    try { handlers.execute { handle(sock) } }
                    catch (_: java.util.concurrent.RejectedExecutionException) {
                        clients.remove(sock)
                        runCatching { sock.close() }
                    }''')
once('''            val ringReader = Timeshift.openReader(target) ?: return''', '''            val expectedGeneration = requestLine.substringAfter("generation=", "")
                .substringBefore('&').substringBefore(' ').toLongOrNull() ?: return
            val ringReader = Timeshift.openReader(target, expectedGeneration) ?: return''')
once('''                while (true) {
                    val n = rr.read(buf)''', '''                while (Timeshift.generation() == expectedGeneration && Timeshift.active) {
                    val n = rr.read(buf)''')
once('''        } finally {
            runCatching { sock.close() }
        }
    }

    @Synchronized
    fun stop()''', '''        } finally {
            clients.remove(sock)
            runCatching { sock.close() }
        }
    }

    @Synchronized
    fun stop()''')
once('''        runCatching { server?.close() }
        server = null''', '''        runCatching { server?.close() }
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        server = null''')
once('?offset=$dvrSourceOffsetBytes"', '?offset=$dvrSourceOffsetBytes&generation=${Timeshift.generation()}"')

a = s.index('            TimeshiftServer.ensureStarted()', s.index('    fun zapTo('))
b = s.index('\n        }\n        // Remember this channel', a)
s = s[:a] + '''            // Stop old playback before enqueueing a new disk writer. Keep all
            // Media3 calls on its application looper; storage stays off this path.
            p.stop()
            p.clearMediaItems()
            val myGen = playbackGen
            Timeshift.start(ctx, tsUrl(ch.url), prefsRef) { dvrStarted ->
                if (myGen != playbackGen || player !== p || !liveMode || simpleRaw) return@start
                if (!dvrStarted) {
                    StabilityCore.note("dvr_storage_unavailable_direct")
                    Timeshift.stop()
                    directLive = true
                    val item = MediaItem.Builder()
                        .setUri(Uri.parse(tsUrl(ch.url)))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
                        .build()
                    p.setMediaItem(item)
                    p.prepare()
                    p.playWhenReady = true
                } else {
                    startDvrWhenPrimed(p, ch, myGen, keepPlaying = true)
                }
            }''' + s[b:]

once('fun canTeeRecording(): Boolean = liveMode && !simpleRaw && !directLive && Timeshift.active && Timeshift.snapshot() != null',
     'fun canTeeRecording(): Boolean = liveMode && !simpleRaw && !directLive && Timeshift.active && (Timeshift.snapshot() != null || Timeshift.isPreparing)')
once('return Timeshift.active && Timeshift.snapshot() != null',
     'return Timeshift.active && (Timeshift.snapshot() != null || Timeshift.isPreparing)')
p.write_text('// RYZOD_V467_NONBLOCKING_DVR\n' + s)
shutil.copyfile('tools/v467/TimeshiftRing.kt', base / 'TimeshiftRing.kt')

# Recording started while storage is opening waits on its IO worker, sharing
# the same provider stream rather than creating a second connection.
r = base / 'Recording.kt'
text = r.read_text()
old = '''        val liveEdge = Timeshift.newestVirtualByte()
        val reader = Timeshift.openReader(liveEdge) ?: return true'''
new = '''        val deadline = android.os.SystemClock.elapsedRealtime() + 12_000L
        while (isActive() && Timeshift.active && Timeshift.generation() == sessionGen &&
            Timeshift.newestVirtualByte() < 188L && android.os.SystemClock.elapsedRealtime() < deadline &&
            (stopAt == null || System.currentTimeMillis() < stopAt)) {
            Thread.sleep(25)
        }
        if (!isActive() || (stopAt != null && System.currentTimeMillis() >= stopAt)) return false
        if (!Timeshift.active || Timeshift.generation() != sessionGen) return true
        val liveEdge = Timeshift.newestVirtualByte()
        val reader = Timeshift.openReader(liveEdge, sessionGen) ?: return true'''
assert text.count(old) == 1
r.write_text(text.replace(old, new, 1))

# Serialize bounded diagnostic writes off the UI/player callbacks. A separate
# tiny crash marker remains synchronous because the process may exit immediately.
log = base / 'StabilityCore.kt'
text = log.read_text()
old = '    @Volatile private var lastScreen = "startup"'
new = '''    @Volatile private var lastScreen = "startup"
    private val logWriter = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 30L, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(128),
        java.util.concurrent.ThreadFactory { r -> Thread(r, "ryzod-diagnostics").apply { isDaemon = true } },
        java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy()
    ).apply { allowCoreThreadTimeOut(true) }'''
assert text.count(old) == 1
text = text.replace(old, new, 1)
old = '            note("FATAL thread=${thread.name} type=${error.javaClass.simpleName}")'
new = '''            runCatching {
                File(context.filesDir, "ryzod_last_crash.log").writeText(
                    "FATAL thread=${thread.name} type=${error.javaClass.simpleName} screen=$lastScreen\\n" +
                        error.stackTrace.take(16).joinToString("\\n")
                )
            }
            note("FATAL thread=${thread.name} type=${error.javaClass.simpleName}")'''
assert text.count(old) == 1
text = text.replace(old, new, 1)
old = '''    fun note(event: String) {
        val context = appContext ?: return
        runCatching {'''
new = '''    fun note(event: String) {
        val context = appContext ?: return
        val screen = lastScreen
        logWriter.execute { writeNote(context, event, screen) }
    }

    private fun writeNote(context: Context, event: String, screen: String) {
        runCatching {'''
assert text.count(old) == 1
text = text.replace(old, new, 1).replace(' | screen=$lastScreen | java=', ' | screen=$screen | java=')
log.write_text(text)

g = Path('app/build.gradle.kts')
text = g.read_text()
assert 'versionCode = 90' in text and 'versionName = "4.66"' in text
g.write_text(text.replace('versionCode = 90', 'versionCode = 91', 1).replace('versionName = "4.66"', 'versionName = "4.67"', 1))
tests = Path('app/src/test/java/com/easyiptv/player')
for test in Path('tools/tests').glob('*Test.kt'):
    shutil.copyfile(test, tests / test.name)
print('Applied RYZOD 4.67 nonblocking session-owned DVR and bounded reader workers')
