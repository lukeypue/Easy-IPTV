#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
m=P.read_text(); g=G.read_text()

# 4.57 correction pass from real Fire TV testing.
m=m.replace('''            Text(
                "Hold OK for Info • Record • Favorite",
                color = SecondaryText,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
''','',1)

# Keep setup keyboard compact for 720p; avoid structural brace rewriting here.
m=m.replace('Modifier.widthIn(max=720.dp).fillMaxWidth(0.78f)', 'Modifier.widthIn(max=640.dp).fillMaxWidth(0.72f)', 1)

# Remaining visible legacy brand text in generated UI only.
m=m.replace('Text("ZAKO",','Text("RYZOD",')
m=m.replace('"ZAKO"','"RYZOD"')

# Version.
m="// RYZOD_V457_FIRETV_CORRECTIONS\n"+m
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 81',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.57"',g,count=1)

P.write_text(m); G.write_text(g)
print("Applied RYZOD 4.57 Fire TV corrections")
