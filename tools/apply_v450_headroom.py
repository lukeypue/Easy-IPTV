#!/usr/bin/env python3
from pathlib import Path
p=Path('app/build.gradle.kts'); s=p.read_text()
# Conservative release-only shrink: keep debug behavior untouched and let R8 remove
# unreachable compatibility/UI code and unused resources. Existing runtime reflection
# is covered by Android/Media3 consumer rules; no playback buffer changes.
s=s.replace('''        release {
            isDebuggable = false
            isMinifyEnabled = false''','''        release {
            isDebuggable = false
            // ZAKO_V450_RELEASE_HEADROOM: shrink only the signed customer build.
            // This reduces dead bytecode/resources without changing DVR/player tuning.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )''')
p.write_text(s)
r=Path('app/proguard-rules.pro')
if not r.exists():
    r.write_text('''# Zako release shrinker rules. Keep Compose/Media3/library consumer rules authoritative.\n# Keep service/receiver entry points named from AndroidManifest.\n-keep class com.easyiptv.player.RecordingService { *; }\n-keep class com.easyiptv.player.DownloadService { *; }\n-keep class com.easyiptv.player.AlarmReceiver { *; }\n-keep class com.easyiptv.player.ScheduleRecoveryReceiver { *; }\n''')
print('Enabled conservative release-only R8/resource shrinking')
