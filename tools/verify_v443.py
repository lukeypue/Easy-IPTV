from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
gradle = Path('app/build.gradle.kts').read_text(encoding='utf-8')

checks = {
    'ring-aware recording eligibility': 'fun canTeeRecording(): Boolean = liveMode && !simpleRaw && !directLive && Timeshift.active && Timeshift.snapshot() != null',
    'ring-aware recording prepare': 'return Timeshift.active && Timeshift.snapshot() != null',
    'ring-aware mini-guide DVR state': 'Timeshift.snapshot() != null && Timeshift.active',
    'legacy single-file canTee check removed': 'fun canTeeRecording(): Boolean = liveMode && !simpleRaw && !directLive && Timeshift.active && Timeshift.file != null',
    'legacy single-file mini-guide check removed': 'Timeshift.file != null && Timeshift.active',
}

missing = []
if checks['ring-aware recording eligibility'] not in main:
    missing.append('ring-aware recording eligibility')
if checks['ring-aware recording prepare'] not in main:
    missing.append('ring-aware recording prepare')
if checks['ring-aware mini-guide DVR state'] not in main:
    missing.append('ring-aware mini-guide DVR state')
if checks['legacy single-file canTee check removed'] in main:
    missing.append('legacy single-file canTee check still present')
if checks['legacy single-file mini-guide check removed'] in main:
    missing.append('legacy single-file mini-guide check still present')
if 'versionCode = 68' not in gradle or 'versionName = "4.43"' not in gradle:
    missing.append('4.43 version identity')

if missing:
    raise SystemExit('VERIFY_V443_FAIL: ' + ', '.join(missing))

print('VERIFY_V443_OK')
