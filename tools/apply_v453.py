#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
m=P.read_text(); g=G.read_text()
if "ZAKO_V453_REMOTE_POLISH" in m: raise SystemExit("already applied")

# Last-channel autoplay is opt-in, not opt-out.
m=m.replace('prefs.getBoolean("autoplay_last", true)','prefs.getBoolean("autoplay_last", false)')
m=m.replace('mutableStateOf(prefs.getBoolean("autoplay_last", true))','mutableStateOf(prefs.getBoolean("autoplay_last", false))')

# Keyboard: compact enough for 720p Fire TV setup screens; selected field stays visible.
m=m.replace('.height(40.dp)\n                            .background(if(selected)', '.height(30.dp)\n                            .background(if(selected)')
m=m.replace('.padding(7.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {', '.padding(4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {')
m=m.replace('fontSize=if(k.length>3)9.sp else 14.sp,', 'fontSize=if(k.length>3)8.sp else 12.sp,')
m=m.replace('Spacer(Modifier.height(6.dp))\n            Column(Modifier.fillMaxWidth()', 'Spacer(Modifier.height(2.dp))\n            Column(Modifier.fillMaxWidth()')

# Live guide program times: cyan, not faint secondary text.
m=m.replace('color = if (isNow) Accent else Muted,\n                                        modifier = Modifier.width(96.dp)',
            'color = if (isNow) Accent else ElectricCyan,\n                                        modifier = Modifier.width(96.dp)')
m=m.replace('color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis',
            'color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis',1)
m=m.replace('color = Muted, fontSize = 8.sp\n                    )\n                    Spacer(Modifier.weight(1f))\n                    Text(if (hasProgramWindow) fmt.format(Date(showEnd)) else "LIVE", color = Muted, fontSize = 8.sp)',
            'color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold\n                    )\n                    Spacer(Modifier.weight(1f))\n                    Text(if (hasProgramWindow) fmt.format(Date(showEnd)) else "LIVE", color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)')

# Confirmation UI deferred to compile-isolated follow-up; preserve existing scheduler structure.\n\n# Record panel edge trapping is handled by focus rules; do not alter structural braces here.\n\n# Preserve existing recording screen structure in this stability release.\n\n# Resumable Range engine from v4.44 is retained; UI resume button will be added without changing its storage model.\n\n# Marker + version.
m=m.replace('ZAKO_V452_CONSOLIDATED','ZAKO_V453_REMOTE_POLISH\n            // ZAKO_V452_CONSOLIDATED',1)
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 77',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.53"',g,count=1)
P.write_text(m); G.write_text(g)
print("Applied 4.53 remote/navigation polish")
