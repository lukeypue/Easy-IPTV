from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
data = Path('app/src/main/java/com/easyiptv/player/Data.kt').read_text(encoding='utf-8')
gradle = Path('app/build.gradle.kts').read_text(encoding='utf-8')

checks = {
    'versionCode 57': 'versionCode = 57' in gradle,
    'versionName 4.32': 'versionName = "4.32"' in gradle,
    'streaming catalog marker': 'ZAKO_V432_STREAMING_CATALOG' in data,
    'JsonReader import': 'import android.util.JsonReader' in data,
    'streaming network reader': 'withJsonReader' in data,
    'unified keyboard marker': 'ZAKO_V432_UNIFIED_KEYBOARD' in main,
    'amazon row digits': '1234567890' in main,
    'amazon row qwerty': 'QWERTYUIOP' in main,
    'lime bubble marker': 'ZAKO_V432_LIME_BUBBLES' in main,
    'startup polish marker': 'ZAKO_V432_STARTUP_POLISH' in main,
    'remembered live queue marker': 'ZAKO_V432_LIVE_QUEUE' in main,
    'mini accessibility marker': 'ZAKO_V432_MINI_ACCESSIBILITY' in main,
    'mini helper removed': '↑/↓ browse • OK tunes • 3 rows stay on screen' not in main,
    'mini program yellow': 'ZAKO_V432_MINI_YELLOW' in main,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(('PASS ' if ok else 'FAIL ') + name)

if failed:
    raise SystemExit('Zako 4.32 verification failed: ' + ', '.join(failed))
print('Zako 4.32 verification passed')
