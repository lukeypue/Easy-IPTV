#!/usr/bin/env python3
"""Apply the wide Fire TV artwork after the complete 4.65 generation chain."""
from pathlib import Path
import shutil


def once(text, old, new):
    if text.count(old) != 1:
        raise SystemExit(f'Expected one 4.66 target, found {text.count(old)}: {old}')
    return text.replace(old, new, 1)


manifest = Path('app/src/main/AndroidManifest.xml')
xml = once(manifest.read_text(),
           'android:banner="@drawable/ryzod_tv_banner_464"',
           'android:banner="@drawable/ryzod_tv_banner_466"')
xml = once(xml, 'android:name=".MainActivity"',
           'android:name=".MainActivity"\n            android:banner="@drawable/ryzod_tv_banner_466"')
manifest.write_text(xml)

gradle = Path('app/build.gradle.kts')
text = once(gradle.read_text(), 'versionCode = 89', 'versionCode = 90')
text = once(text, 'versionName = "4.65"', 'versionName = "4.66"')
gradle.write_text(text)

tests = Path('app/src/test/java/com/easyiptv/player')
tests.mkdir(parents=True, exist_ok=True)
shutil.copyfile('tools/tests/LauncherArtworkTest.kt', tests / 'LauncherArtworkTest.kt')
print('Applied RYZOD 4.66 full-width Fire TV launcher artwork')
