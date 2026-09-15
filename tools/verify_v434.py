from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text()
gradle = Path('app/build.gradle.kts').read_text()
checks = {
    'versionCode 59': 'versionCode = 59' in gradle,
    'versionName 4.34': 'versionName = "4.34"' in gradle,
    'mini header layout marker': 'ZAKO_V434_MINI_HEADER' in main,
    'current program yellow': 'ZAKO_V434_MINI_NOW_YELLOW' in main and 'Color(0xFFFFE45C)' in main,
    'mini info button': 'ZAKO_V434_MINI_INFO' in main and '"ⓘ INFO"' in main,
    'mini current info dialog': 'private fun MiniGuideNowInfoDialog(' in main,
    'mini info description': 'nowShow.desc.ifBlank' in main,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit('v4.34 verification failed: ' + ', '.join(missing))
print('v4.34 verification passed')
