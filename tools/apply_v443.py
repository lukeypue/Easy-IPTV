from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')

main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')


def once(text: str, old: str, new: str, label: str) -> str:
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, found {n}')
    return text.replace(old, new, 1)

# ZAKO_V443_DVR_RING_RECORD_FIX
# v4.40 replaced the old single growing Timeshift.file with TimeshiftRing segments.
# Playback/FF/RW were already ring-aware, but recording eligibility still required
# Timeshift.file != null. That compatibility field is intentionally always null,
# so REC falsely reported Smooth Live even while DVR Live was working.
main = once(
    main,
    'fun canTeeRecording(): Boolean = liveMode && !simpleRaw && !directLive && Timeshift.active && Timeshift.file != null',
    'fun canTeeRecording(): Boolean = liveMode && !simpleRaw && !directLive && Timeshift.active && Timeshift.snapshot() != null',
    'ring-aware canTeeRecording'
)
main = once(
    main,
    '        return Timeshift.active && Timeshift.file != null',
    '        return Timeshift.active && Timeshift.snapshot() != null',
    'ring-aware prepareCurrentForRecording'
)
main = once(
    main,
    '        Timeshift.file != null && Timeshift.active',
    '        Timeshift.snapshot() != null && Timeshift.active',
    'ring-aware mini-guide DVR state'
)

# Keep visible source identity accurate when this generated build is inspected.
main = main.replace('Zako 4.42 — plays the playlists you provide.', 'Zako 4.43 — plays the playlists you provide.')
MAIN.write_text(main, encoding='utf-8')

gradle, n1 = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 68', gradle, count=1)
gradle, n2 = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.43"', gradle, count=1)
if n1 != 1 or n2 != 1:
    raise SystemExit('v4.43 version bump failed')
GRADLE.write_text(gradle, encoding='utf-8')

print('Applied Zako 4.43 DVR ring recording fix')
