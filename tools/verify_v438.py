from pathlib import Path
import re
import sys

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
GRADLE = Path('app/build.gradle.kts').read_text(encoding='utf-8')

checks = {
    'versionCode 63': bool(re.search(r'versionCode\s*=\s*63\b', GRADLE)),
    'versionName 4.38': 'versionName = "4.38"' in GRADLE,
    'steady hotfix marker': 'ZAKO_V438_STEADY_CRASH_HOTFIX' in MAIN,
    'no forced direct steady': 'if (steadyRecovery) directLive = true' not in MAIN,
    'no guessed steady hls': 'val steadyCandidate = steadyRecovery && !steadyHlsFailed && ch.url.endsWith(".ts", ignoreCase = true)' not in MAIN,
    'no recursive zap in steady hls error': 'steady_hls_fallback' not in MAIN,
    'steady start diagnostic': 'steady_start_safe_ts' in MAIN,
    'player error diagnostic': 'steady_player_error_safe' in MAIN,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    print('v4.38 verification failed:', ', '.join(failed))
    sys.exit(1)
print('v4.38 Steady crash hotfix verification passed')
