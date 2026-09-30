#!/usr/bin/env python3
"""Make channel EPG requests cancellable after the core/memory/review 4.67 patches.

--baseline validates the same anchors without changing behavior, so the real
HTTP cancellation tests can run against the existing implementation first.
"""
import argparse
from pathlib import Path


OLD_EPG = '''    override suspend fun epg(channelId: String, limit: Int): List<EpgEntry> =
        withContext(Dispatchers.IO) {
            try {
                var out = runCatching {
                    parseEpgListings(Net.get(api("get_short_epg") + "&stream_id=$channelId&limit=$limit"))
                }.getOrDefault(emptyList())

                if (out.isEmpty()) {
                    // Fallback: full-day guide table, then keep shows that haven't ended yet.
                    out = runCatching {
                        parseEpgListings(Net.get(api("get_simple_data_table") + "&stream_id=$channelId"))
                    }.getOrDefault(emptyList())
                    val now = System.currentTimeMillis()
                    out = out.filter { it.endMs >= now }.sortedBy { it.startMs }.take(limit)
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }
'''

NEW_EPG = '''    override suspend fun epg(channelId: String, limit: Int): List<EpgEntry> =
        withContext(Dispatchers.IO) {
            suspend fun requestEntries(url: String): List<EpgEntry> = try {
                val body = EpgRequests.get(url)
                currentCoroutineContext().ensureActive()
                parseEpgListings(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                emptyList()
            }

            try {
                var out = requestEntries(api("get_short_epg") + "&stream_id=$channelId&limit=$limit")
                currentCoroutineContext().ensureActive()
                if (out.isEmpty()) {
                    // Keep the existing fallback for a current channel, never for
                    // a request cancelled because the viewer has already moved on.
                    out = requestEntries(api("get_simple_data_table") + "&stream_id=$channelId")
                    val now = System.currentTimeMillis()
                    out = out.filter { it.endMs >= now }.sortedBy { it.startMs }.take(limit)
                }
                currentCoroutineContext().ensureActive()
                out
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                emptyList()
            }
        }
'''

OLD_EFFECT = '''    LaunchedEffect(current.epgId, EpgStore.loaded.value) {
        if (!current.isLive) return@LaunchedEffect
        val fromGuide = EpgStore.guide(current.guideKey, current.name)
        if (fromGuide.isNotEmpty()) {
            nowNext = fromGuide.take(2)
        } else {
            val id = current.epgId
            if (id != null && source != null && source.supportsEpg) {
                nowNext = source.epg(id, 2)
            }
        }
    }
'''

NEW_EFFECT = '''    LaunchedEffect(current, source, EpgStore.loaded.value) {
        nowNext = emptyList()
        if (!current.isLive) return@LaunchedEffect
        val fromGuide = EpgStore.guide(current.guideKey, current.name)
        if (fromGuide.isNotEmpty()) {
            nowNext = fromGuide.take(2)
        } else {
            val id = current.epgId
            if (id != null && source != null && source.supportsEpg) {
                // Local guide results stay immediate. A brief settle delay avoids
                // provider guide requests for channels passed during rapid zapping.
                kotlinx.coroutines.delay(250)
                nowNext = source.epg(id, 2)
            }
        }
    }
'''


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise ValueError(f'Expected one 4.67 EPG {label} anchor, found {count}')
    return text.replace(old, new, 1)


def patch_data(text: str, baseline: bool = False) -> str:
    text = once(text, OLD_EPG, OLD_EPG if baseline else NEW_EPG, 'provider method')
    old = 'import kotlinx.coroutines.Dispatchers\n'
    new = ('import kotlinx.coroutines.CancellationException\n'
           'import kotlinx.coroutines.Dispatchers\n'
           'import kotlinx.coroutines.currentCoroutineContext\n'
           'import kotlinx.coroutines.ensureActive\n')
    return once(text, old, old if baseline else new, 'coroutine imports')


def patch_main(text: str, baseline: bool = False) -> str:
    old = '    var nowNext by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }\n'
    new = '    var nowNext by remember(current, source) { mutableStateOf<List<EpgEntry>>(emptyList()) }\n'
    text = once(text, old, old if baseline else new, 'channel state')
    return once(text, OLD_EFFECT, OLD_EFFECT if baseline else NEW_EFFECT, 'channel effect')


def apply(root: Path = Path('.'), baseline: bool = False) -> None:
    directory = root / 'app/src/main/java/com/easyiptv/player'
    data = directory / 'Data.kt'
    activity = directory / 'MainActivity.kt'
    # Check all anchors before writing either application source file.
    patched_data = patch_data(data.read_text(), baseline)
    patched_main = patch_main(activity.read_text(), baseline)
    if baseline:
        print('Validated RYZOD 4.67 EPG baseline; existing request behavior unchanged')
        return
    helper = (Path(__file__).resolve().parent / 'v467/EpgRequests.kt').read_text()
    (directory / 'EpgRequests.kt').write_text(helper)
    data.write_text(patched_data)
    activity.write_text(patched_main)
    print('Applied RYZOD 4.67 EPG request cancellation and channel-state cleanup')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', action='store_true', help='validate anchors but keep the old behavior for regression tests')
    args = parser.parse_args()
    apply(baseline=args.baseline)
