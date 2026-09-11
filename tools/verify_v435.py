from pathlib import Path

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text()
gradle = GRADLE.read_text()

checks = {
    'versionCode 60': 'versionCode = 60' in gradle,
    'versionName 4.35': 'versionName = "4.35"' in gradle,
    'horizontal lineup marker': 'ZAKO_V435_LINEUP_HORIZONTAL' in main,
    'horizontal weighted row': 'Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically)' in main,
    'program remains bright yellow': 'rowProgram.title' in main and 'Color(0xFFFFE45C)' in main,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('v4.35 verification failed: ' + ', '.join(failed))
print('v4.35 verification passed')
