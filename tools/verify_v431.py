from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
gradle = Path('app/build.gradle.kts').read_text(encoding='utf-8')

checks = {
    'version 4.31': 'versionName = "4.31"' in gradle,
    'version code 56': 'versionCode = 56' in gradle,
    'neon guide help': 'Hold OK for Info • Record • Favorite' in main and 'NeonGreen' in main,
    'live info action': 'ZAKO_V431_LIVE_INFO' in main,
    'mini guide current title': 'ZAKO_V431_MINI_TITLE' in main,
    'cache resistant updater': 'ZAKO_V431_FRESH_UPDATE' in main,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if failed:
    raise SystemExit('Missing v4.31 behavior: ' + ', '.join(failed))
print('Zako 4.31 verification passed')
