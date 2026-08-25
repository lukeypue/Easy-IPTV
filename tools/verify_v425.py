from pathlib import Path

main = Path("app/src/main/java/com/easyiptv/player/MainActivity.kt").read_text(encoding="utf-8")
data = Path("app/src/main/java/com/easyiptv/player/Data.kt").read_text(encoding="utf-8")

required_main = {
    "focus fix marker": "ZAKO_V425_FOCUS_FIX",
    "program cyan": "ProgramCyan",
    "download green": "DownloadGreen",
    "mini guide EPG row": "ZAKO_V425_MINI_EPG",
    "live wrap": "ZAKO_V425_LIVE_WRAP",
    "grid guide": "private fun LiveGridGuide(",
    "movie info dialog": "private fun VodInfoDialog(",
    "touch DVR": "ZAKO_V425_TOUCH_DVR",
    "recording IO scanner": "scanRecordings",
    "recording preview guard": "Recorder.activeName.value == null",
    "memory trim": "override fun onTrimMemory(level: Int)",
}

for label, needle in required_main.items():
    if needle not in main:
        raise SystemExit(f"v4.25 feature verification failed: missing {label}: {needle}")

required_data = {
    "MediaInfo model": "data class MediaInfo(",
    "Source mediaInfo interface": "suspend fun mediaInfo(movieId: String): MediaInfo?",
    "Xtream lazy VOD info": "get_vod_info",
}
for label, needle in required_data.items():
    if needle not in data:
        raise SystemExit(f"v4.25 feature verification failed: missing {label}: {needle}")

# The v4.24 whole-content Left fallback caused poster-grid Left presses to
# bubble out to the category rail. v4.25+ must keep that broad handler removed.
bad_fallback = '''Modifier\n                        .weight(1f)\n                        .onKeyEvent { ev ->\n                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {\n                                runCatching { railFocus.requestFocus() }'''
if bad_fallback in main:
    raise SystemExit("v4.25 feature verification failed: broad Home content Left fallback is still present")

# Guide/touch controls reuse the existing Playback/EpgStore paths; they must not
# accidentally introduce another ExoPlayer instance.
player_builders = main.count("ExoPlayer.Builder(")
if player_builders != 1:
    raise SystemExit(f"v4.25 feature verification failed: expected one ExoPlayer.Builder, found {player_builders}")

if "EpgStore.guide" not in main:
    raise SystemExit("v4.25 feature verification failed: guide UI is not using EpgStore")

print("Zako v4.25+ feature verification passed")
print(" - deterministic poster focus guard present")
print(" - live wrap + Mini Guide EPG + grid guide present")
print(" - lazy movie info present")
print(" - touch DVR reuses shared Playback")
print(" - recording/memory hardening present")
