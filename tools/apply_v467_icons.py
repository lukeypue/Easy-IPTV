#!/usr/bin/env python3
"""Expose one explicit launcher artwork path without adding launcher aliases."""
from pathlib import Path

res = Path('app/src/main/res')
# Keep the approved phone resources. A television qualifier wins over v26;
# TV resolves to a plain legacy BitmapDrawable with intrinsic wide proportions.
for folder in ('mipmap-anydpi', 'mipmap-anydpi-v26'):
    (res / folder / 'ryzod_launcher_467.xml').write_text(
        (res / folder / 'ryzod_launcher_464.xml').read_text())
wide = '''<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/ryzod_tv_artwork_466"
    android:gravity="fill" android:filter="true" />
'''
(res / 'mipmap-television-anydpi' / 'ryzod_launcher_467.xml').write_text(wide)
(res / 'drawable' / 'ryzod_tv_banner_467.xml').write_text(wide)

manifest = Path('app/src/main/AndroidManifest.xml')
s = manifest.read_text()
assert s.count('@mipmap/ryzod_launcher_464') == 2
assert s.count('@drawable/ryzod_tv_banner_466') == 2
s = s.replace('@mipmap/ryzod_launcher_464', '@mipmap/ryzod_launcher_467')
s = s.replace('@drawable/ryzod_tv_banner_466', '@drawable/ryzod_tv_banner_467')
s = s.replace('android:name=".MainActivity"', '''android:name=".MainActivity"
            android:icon="@mipmap/ryzod_launcher_467"
            android:logo="@mipmap/ryzod_launcher_467"''', 1)
s = s.replace('<intent-filter>', '<intent-filter android:icon="@mipmap/ryzod_launcher_467">', 1)
manifest.write_text(s)


# RYZOD 4.68: Fire TV/Amazon launchers are wide banners, not adaptive phone icons.
# Use the 320x180 TV artwork for both the television-qualified launcher and banner.
# The phone keeps the approved adaptive launcher through the unqualified folders.
\nprint('Applied explicit launch activity/filter icons and 320x180 wide TV artwork; retained phone adaptive logo')
