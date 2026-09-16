from pathlib import Path
import re

DL = Path('app/src/main/java/com/easyiptv/player/Downloads.kt')
MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
d = DL.read_text(encoding='utf-8'); m = MAIN.read_text(encoding='utf-8'); g = GRADLE.read_text(encoding='utf-8')

def once(text, old, new, label):
    n=text.count(old)
    if n != 1: raise SystemExit(f'{label}: expected 1 match, found {n}')
    return text.replace(old,new,1)

# Preserve interrupted partial bytes.
d = once(d, '''                    runCatching { File(item.path + ".part").delete() }
                    mark(context, item.id, STATE_FAILED, 0L, -1L, "Download was interrupted. Start it again.")''', '''                    val part = File(item.path + ".part")
                    val partialBytes = if (part.exists()) part.length() else 0L
                    mark(context, item.id, STATE_FAILED, partialBytes, progress(context, item.id)?.second ?: -1L,
                        "Download was interrupted. Resume it from Downloads.")''', 'interrupted partial preservation')

d = once(d, '''                    else -> remove(prefs, existing, context)
                }
            }
''', '''                    state(context, existing.id) == STATE_FAILED && existing.url.isNotBlank() -> {
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
''', 'failed item resume')

d = once(d, '''                runCatching { part.delete() }
                runCatching { finalFile.delete() }
                DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, 0L, -1L)''', '''                val resumeFrom = if (part.exists()) part.length().coerceAtLeast(0L) else 0L
                runCatching { finalFile.delete() }
                done = resumeFrom
                DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, done, -1L)''', 'preserve partial at begin')

# v4.28+ already sends Range bytes=0-. Make that header resume-aware while retaining all media headers.
old_range = '.header("Range", "bytes=0-")'
new_range = '.apply { if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-") else header("Range", "bytes=0-") }'
if d.count(old_range) != 1: raise SystemExit(f'range header expected 1 match, found {d.count(old_range)}')
d = d.replace(old_range, new_range, 1)

d = once(d, '''                    total = body.contentLength()
                    DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, 0L, total)

                    body.byteStream().use { inp ->
                        FileOutputStream(part, false).use { out ->
                            val buf = ByteArray(128 * 1024)''', '''                    val append = resumeFrom > 0L && r.code == 206
                    if (resumeFrom > 0L && !append) done = 0L
                    val responseBytes = body.contentLength()
                    total = if (append && responseBytes >= 0L) resumeFrom + responseBytes else responseBytes
                    DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, done, total)

                    body.byteStream().use { inp ->
                        FileOutputStream(part, append).use { out ->
                            val buf = ByteArray(64 * 1024)''', 'append partial response')

d = once(d, '''                if (!userCancelled) {
                    runCatching { part.delete() }
                    DownloadStore.mark(''', '''                if (!userCancelled) {
                    val partialDone = if (part.exists()) part.length() else done
                    DownloadStore.mark(''', 'keep failed partial')
d = d.replace('''                        done,
                        total,
                        t.message ?: "Download failed"''', '''                        partialDone,
                        total,
                        (t.message ?: "Download failed") + if (partialDone > 0L) " — Resume is available." else ""''', 1)

g, n1 = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 69', g, count=1)
g, n2 = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.44"', g, count=1)
if n1 != 1 or n2 != 1: raise SystemExit('v4.44 version bump failed')
DL.write_text(d,encoding='utf-8'); MAIN.write_text(m,encoding='utf-8'); GRADLE.write_text(g,encoding='utf-8')
print('Applied Zako 4.44 resumable download stability fix on full generated source')
