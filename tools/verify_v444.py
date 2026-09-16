from pathlib import Path
import re

d = Path('app/src/main/java/com/easyiptv/player/Downloads.kt').read_text(encoding='utf-8')
g = Path('app/build.gradle.kts').read_text(encoding='utf-8')
checks = {
    'versionCode 69': bool(re.search(r'versionCode\s*=\s*69\b', g)),
    'versionName 4.44': 'versionName = "4.44"' in g,
    'Range resume': 'header("Range", "bytes=$resumeFrom-")' in d,
    '206 append gate': 'resumeFrom > 0L && r.code == 206' in d,
    '64 KiB buffer': 'ByteArray(64 * 1024)' in d,
    'failed partial preserved': 'val partialDone = if (part.exists()) part.length() else done' in d,
    'no begin partial delete': 'runCatching { part.delete() }\n                runCatching { finalFile.delete() }' not in d,
    'resume message': 'Resume is available.' in d,
}
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('Zako 4.44 verification failed: '+', '.join(failed))
print('Zako 4.44 verification passed: '+', '.join(checks))
