#!/usr/bin/env python3
"""Check the actual optimized APK, in addition to pre-shrink resource tests."""
from pathlib import Path
import re
import sys

manifest, resources = (Path(p).read_text() for p in sys.argv[1:])
activity = re.search(r'E: activity .*?(?=\n\s+E: (?:provider|service|receiver|activity)|\Z)', manifest, re.S)
assert activity and 'com.easyiptv.player.MainActivity' in activity.group(), 'Missing launch activity'
text = activity.group()
assert 'android.intent.category.LAUNCHER' in text
assert 'android.intent.category.LEANBACK_LAUNCHER' in text
for attribute in ('icon', 'logo', 'banner'):
    assert re.search(rf'A: android:{attribute}\(.*?\)=@0x[0-9a-f]+', text), f'Missing explicit activity {attribute}'
assert 'mipmap/ryzod_launcher_467' in resources, 'Optimized APK lost launcher icon'
assert 'drawable/ryzod_tv_banner_467' in resources, 'Optimized APK lost TV banner'
assert 'drawable/ryzod_tv_artwork_466' in resources, 'Optimized APK lost approved TV artwork'
assert 'config television-anydpi' in resources, 'Optimized APK lost TV qualifier'
print('Optimized APK retains explicit launch artwork and television resource configuration')
