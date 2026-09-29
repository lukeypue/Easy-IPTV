#!/usr/bin/env python3
"""Wire the real saved-item rows after generating the complete 4.64 baseline."""
from pathlib import Path
import re

p = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
m = p.read_text()

def once(s, old, new):
    if s.count(old) != 1:
        raise SystemExit(f'Expected one 4.65 target, found {s.count(old)}: {old[:100]}')
    return s.replace(old, new, 1)

start = m.index('fun DownloadsPane(')
end = m.index('/* ----------------------------- recordings', start)
d = m[start:end]
d = once(d, 'items(items) { d ->', 'items(items, key = { it.id }) { d ->')
d = once(d, '                    val btnFocus = remember { FocusRequester() }\n', '')
d = once(d, '                            .focusProperties { right = btnFocus }\n', '')
d = once(d, '.clickable(enabled = ready) { onPlay(Playable(d.title, d.path, isLive = false)) }',
         '.clickable { selectedDownload = d }')
a = d.index('                        if (DownloadStore.state(context,d.id)==DownloadStore.STATE_FAILED && !ready) {')
b = d.index('\n                    }\n                }', a)
d = d[:a] + '                        Text("OPTIONS ›", color = Muted, fontSize = 11.sp)\n' + d[b:]
a = d.index('        confirmDownload?.let { d ->')
d = d[:a] + '''    confirmDownload?.let { d ->
        ConfirmSavedItemDelete(d.title, "download", onCancel = { confirmDownload = null }) {
            DownloadStore.stopAndRemove(context, prefs, d)
            items = DownloadStore.load(prefs)
            confirmDownload = null
        }
    }
    selectedDownload?.let { d ->
        val ready = DownloadStore.isReady(context, d)
        val inFlight = DownloadStore.isInFlight(context, d.id)
        SavedItemPopup(
            title = d.title,
            message = if (ready) "Saved for offline watching." else "Finish downloading to play. Pause keeps your progress; Resume continues the download.",
            actions = buildList {
                add(SavedItemAction("PLAY", enabled = ready) {
                    selectedDownload = null
                    onPlay(Playable(d.title, d.path, isLive = false))
                })
                if (!ready) {
                    add(SavedItemAction("RESUME", enabled = !inFlight) {
                        toast(context, DownloadStore.resume(context, prefs, d))
                        items = DownloadStore.load(prefs)
                        selectedDownload = null
                    })
                    add(SavedItemAction("PAUSE DOWNLOAD", enabled = inFlight) {
                        toast(context, DownloadStore.pause(context, prefs, d))
                        items = DownloadStore.load(prefs)
                        selectedDownload = null
                    })
                }
                add(SavedItemAction("DELETE", destructive = true) {
                    selectedDownload = null
                    confirmDownload = d
                })
                add(SavedItemAction("CLOSE") { selectedDownload = null })
            },
            onClose = { selectedDownload = null }
        )
    }
}

'''
m = m[:start] + d + m[end:]

start = m.index('fun RecordingsPane(')
end = m.index('/* ----------------------------- playlists', start)
r = m[start:end]
r = once(r, '    val context = LocalContext.current', '    val context = LocalContext.current\n    val scope = rememberCoroutineScope()')
r = once(r, 'items(files) { f ->', 'items(files, key = { it.absolutePath }) { f ->')
r = once(r, '                    val trashFocus = remember { FocusRequester() }\n', '')
r = once(r, '                            .focusProperties { right = trashFocus }\n', '')
r = once(r, '.clickable { onPlay(Playable(f.nameWithoutExtension, f.absolutePath, isLive = false)) }',
         '.clickable { selectedRecordingFile = f }')
a = r.index('                        IconButton(\n                            modifier = Modifier.focusRequester(trashFocus)')
b = r.index('\n                    }\n                }', a)
r = r[:a] + '                        Text("OPTIONS ›", color = Muted, fontSize = 11.sp)\n' + r[b:]
r = once(r, '                    Recorder.stop(context)\n', '                    confirmStopRecording = true\n')
a = r.index('        selectedRecordingFile?.let { f ->')
b = r.index('        confirmStopRecording.takeIf', a)
r = r[:a] + '''        selectedRecordingFile?.let { f ->
            SavedItemPopup(
                title = f.nameWithoutExtension.removePrefix("REC_").replace('_', ' '),
                message = "Choose an option for this recording.",
                actions = listOf(
                    SavedItemAction("PLAY") {
                        selectedRecordingFile = null
                        onPlay(Playable(f.nameWithoutExtension, f.absolutePath, isLive = false))
                    },
                    SavedItemAction("DELETE", destructive = true) {
                        selectedRecordingFile = null
                        confirmRecordingFile = f
                    },
                    SavedItemAction("CLOSE") { selectedRecordingFile = null }
                ),
                onClose = { selectedRecordingFile = null }
            )
        }
''' + r[b:]
a = r.index('        confirmRecordingFile?.let { f ->')
r = r[:a] + '''        confirmRecordingFile?.let { f ->
            ConfirmSavedItemDelete(
                f.nameWithoutExtension.removePrefix("REC_").replace('_', ' '),
                "recording", onCancel = { confirmRecordingFile = null }
            ) {
                confirmRecordingFile = null
                scope.launch {
                    val deleted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        !f.exists() || f.delete()
                    }
                    files = scanRecordings()
                    if (!deleted) toast(context, "Couldn't delete the recording. Check that the storage is connected.")
                }
            }
        }
    }
}

'''
m = m[:start] + r + m[end:]
fragment = Path('tools/v465/SavedItemDialogs.kt.fragment').read_text()
m = once(m, '/* ----------------------------- downloads ----------------------------- */',
         fragment + '\n\n/* ----------------------------- downloads ----------------------------- */')
p.write_text('// RYZOD_V465_SAVED_ITEM_MENUS\n' + m)
g = Path('app/build.gradle.kts')
s = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 89', g.read_text(), count=1)
s = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.65"', s, count=1)
g.write_text(s)
print('Applied RYZOD 4.65 saved item menus and delete confirmations')
