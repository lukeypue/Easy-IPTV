#!/usr/bin/env python3
from pathlib import Path
p=Path("app/src/main/java/com/easyiptv/player/Downloads.kt")
s=p.read_text()
s=s.replace('runCatching { File(item.path + ".part").delete() }\n                    mark(context, item.id, STATE_FAILED, 0L, -1L, "Download was interrupted. Start it again.")','val partial = File(item.path + ".part").length().coerceAtLeast(0L)\n                    mark(context, item.id, STATE_FAILED, partial, -1L, "Download was interrupted. Press Resume when your connection is back.")',1)
needle='''    fun stopAndRemove(context: Context, prefs: SharedPreferences, item: Item) {
        if (isInFlight(context, item.id)) DownloadService.cancel(context, item.id)
        remove(prefs, item, context)
    }
'''
rep=needle+'''
    fun resume(context: Context, prefs: SharedPreferences, item: Item): String {
        if (item.url.isBlank()) return "This older download cannot resume. Add the title again."
        if (isReady(context, item)) return "Already downloaded."
        if (isInFlight(context, item.id)) return "Already downloading or queued."
        val part = File(item.path + ".part")
        mark(context, item.id, STATE_PENDING, part.length().coerceAtLeast(0L), -1L, "")
        val started = kickQueue(context, prefs)
        return if (started) "Resuming download…" else "Queued to resume."
    }
'''
if needle not in s: raise SystemExit("stopAndRemove anchor missing")
s=s.replace(needle,rep,1)
s=s.replace('mark(context, next.id, STATE_RUNNING, 0L, -1L, "")\n            DownloadService.start(context, next.id, next.title, next.url, next.path)','val partial = File(next.path + ".part").length().coerceAtLeast(0L)\n            mark(context, next.id, STATE_RUNNING, partial, -1L, "")\n            DownloadService.start(context, next.id, next.title, next.url, next.path)',1)
s=s.replace('''                finalFile.parentFile?.mkdirs()
                runCatching { part.delete() }
                runCatching { finalFile.delete() }
                DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, 0L, -1L)''','''                finalFile.parentFile?.mkdirs()
                runCatching { finalFile.delete() }
                done = part.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
                DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, done, -1L)''',1)
s=s.replace('val req = Request.Builder().url(url).header("User-Agent", ua).build()','val rb = Request.Builder().url(url).header("User-Agent", ua)\n                    if (done > 0L) rb.header("Range", "bytes=$done-")\n                    val req = rb.build()',1)
s=s.replace('''                    val body = r.body ?: throw IOException("Empty response")
                    total = body.contentLength()
                    DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, 0L, total)

                    body.byteStream().use { inp ->
                        FileOutputStream(part, false).use { out ->''','''                    val body = r.body ?: throw IOException("Empty response")
                    val appending = done > 0L && r.code == 206
                    if (done > 0L && !appending) { done = 0L; runCatching { part.delete() } }
                    val responseBytes = body.contentLength()
                    total = if (responseBytes > 0L) done + responseBytes else -1L
                    DownloadStore.mark(this@DownloadService, id, DownloadStore.STATE_RUNNING, done, total)

                    body.byteStream().use { inp ->
                        FileOutputStream(part, appending).use { out ->''',1)
s=s.replace('if (!userCancelled) {\n                    runCatching { part.delete() }\n                    DownloadStore.mark(','if (!userCancelled) {\n                    DownloadStore.mark(',1)
p.write_text(s)
print("Applied resumable RYZOD 4.60 downloads")
