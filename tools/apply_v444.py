from pathlib import Path
import re

DL = Path('app/src/main/java/com/easyiptv/player/Downloads.kt')
MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')

d = DL.read_text(encoding='utf-8')
m = MAIN.read_text(encoding='utf-8')
g = GRADLE.read_text(encoding='utf-8')

def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, found {n}')
    return text.replace(old, new, 1)

# ZAKO_V444_DOWNLOAD_RESUME: interrupted native transfers retain their .part bytes.
d = once(d,
'''                    runCatching { File(item.path + ".part").delete() }
                    mark(context, item.id, STATE_FAILED, 0L, -1L, "Download was interrupted. Start it again.")''',
'''                    val part = File(item.path + ".part")
                    val partialBytes = if (part.exists()) part.length() else 0L
                    mark(context, item.id, STATE_FAILED, partialBytes, progress(context, item.id)?.second ?: -1L,
                        if (partialBytes > 0L) "Download was interrupted. Resume it from Downloads."
                        else "Download was interrupted. Resume it from Downloads.")''',
'interrupted partial preservation')

# Failed/partial items are resumed instead of being deleted/recreated when selected again.
d = once(d,
'''                    else -> remove(prefs, existing, context)
                }
            }
''',
'''                    state(context, existing.id) == STATE_FAILED && existing.url.isNotBlank() -> {
                        mark(context, existing.id, STATE_PENDING,
                            File(existing.path + ".part").let { if (it.exists()) it.length() else 0L },
                            progress(context, existing.id)?.second ?: -1L, "")
                        val started = kickQueue(context, prefs)
                        return if (started) "Resuming \\\"$title\\\" from where it stopped."
                        else "Queued \\\"$title\\\" to resume."
                    }
                    else -> remove(prefs, existing, context)
                }
            }
''',
'failed item resume')

# Resume HTTP transfer using Range. A server that ignores Range safely restarts at zero.
d = once(d,
'''                runCatching { part.delete() }
                runCatching { finalFile.delete() }
                DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, 0L, -1L)''',
'''                val resumeFrom = if (part.exists()) part.length().coerceAtLeast(0L) else 0L
                runCatching { finalFile.delete() }
                done = resumeFrom
                DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, done, -1L)''',
'preserve partial at begin')

d = once(d,
'''                fun executeWithUa(ua: String): okhttp3.Response {
                    val req = Request.Builder().url(url).header("User-Agent", ua).build()''',
'''                fun executeWithUa(ua: String, offset: Long): okhttp3.Response {
                    val rb = Request.Builder().url(url).header("User-Agent", ua)
                    if (offset > 0L) rb.header("Range", "bytes=$offset-")
                    val req = rb.build()''',
'range request builder')

d = d.replace('var resp = executeWithUa(Net.UA)', 'var resp = executeWithUa(Net.UA, resumeFrom)', 1)
d = d.replace('''resp = executeWithUa(
                        "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
                    )''', '''resp = executeWithUa(
                        "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36",
                        resumeFrom
                    )''', 1)

d = once(d,
'''                    total = body.contentLength()
                    DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, 0L, total)

                    body.byteStream().use { inp ->
                        FileOutputStream(part, false).use { out ->
                            val buf = ByteArray(128 * 1024)''',
'''                    val append = resumeFrom > 0L && r.code == 206
                    if (resumeFrom > 0L && !append) {
                        done = 0L
                    }
                    val responseBytes = body.contentLength()
                    total = if (append && responseBytes >= 0L) resumeFrom + responseBytes else responseBytes
                    DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, done, total)

                    body.byteStream().use { inp ->
                        FileOutputStream(part, append).use { out ->
                            val buf = ByteArray(64 * 1024)''',
'append partial response')

# Ordinary network/provider failure keeps the partial file for a later resume.
d = once(d,
'''                if (!userCancelled) {
                    runCatching { part.delete() }
                    DownloadStore.mark(''',
'''                if (!userCancelled) {
                    val partialDone = if (part.exists()) part.length() else done
                    DownloadStore.mark(''',
'keep failed partial')
d = d.replace('''                        done,
                        total,
                        t.message ?: "Download failed"''', '''                        partialDone,
                        total,
                        (t.message ?: "Download failed") + if (partialDone > 0L) " — Resume is available." else ""''', 1)

# Version identity.
m = m.replace('Zako 4.43 — plays the playlists you provide.', 'Zako 4.44 — plays the playlists you provide.')
g, n1 = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 69', g, count=1)
g, n2 = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.44"', g, count=1)
if n1 != 1 or n2 != 1:
    raise SystemExit('v4.44 version bump failed')

DL.write_text(d, encoding='utf-8')
MAIN.write_text(m, encoding='utf-8')
GRADLE.write_text(g, encoding='utf-8')
print('Applied Zako 4.44 resumable download stability fix')
