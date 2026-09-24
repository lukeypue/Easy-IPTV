#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
m=P.read_text(); g=G.read_text()\nif "import androidx.compose.foundation.layout.widthIn" not in m:\n    m=m.replace("import androidx.compose.foundation.layout.width", "import androidx.compose.foundation.layout.width\\nimport androidx.compose.foundation.layout.widthIn", 1)

# RYZOD 4.56 visual/usability pass. Apply only after the verified 4.55 chain.
# One branded identity everywhere instead of legacy text.
m=m.replace('Text("RYZOD", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Ink)', 'RyzodBrandMark(compact = false)')
m=m.replace('Text("RYZOD", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)', 'RyzodBrandMark(compact = true)')
m=m.replace('title = { Text("Leave RYZOD?", color = Ink) }', 'title = { Row(verticalAlignment = Alignment.CenterVertically) { RyzodBrandMark(compact = true); Spacer(Modifier.width(10.dp)); Text("Exit?", color = Ink) } }')

# Compact Fire-TV keyboard: fixed-width overlay above content, not a full-screen-width slab.
kbd_old='''            Column(Modifier.fillMaxWidth().background(Color(0xF20A2038),RoundedCornerShape(18.dp))
                .border(2.dp,ElectricCyan.copy(alpha=.60f),RoundedCornerShape(18.dp))
                .focusRequester(keyFocus).focusable().onPreviewKeyEvent { ev ->'''
kbd_new='''            Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center) {
            Column(Modifier.widthIn(max=720.dp).fillMaxWidth(0.78f).background(Color(0xF20A2038),RoundedCornerShape(18.dp))
                .border(2.dp,ElectricCyan.copy(alpha=.60f),RoundedCornerShape(18.dp))
                .focusRequester(keyFocus).focusable().onPreviewKeyEvent { ev ->'''
if kbd_old not in m: raise SystemExit("v4.56 keyboard open target missing")
m=m.replace(kbd_old,kbd_new,1)
kbd_close='''                Text("D-pad moves • OK types • Left/Right wraps • Back closes",color=ElectricCyan,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
            }
        }
    }
}'''
kbd_close_new='''                Text("D-pad moves • OK types • Left/Right wraps • Back closes",color=ElectricCyan,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
            }
            }
        }
    }
}'''
if kbd_close not in m: raise SystemExit("v4.56 keyboard close target missing")
m=m.replace(kbd_close,kbd_close_new,1)

# The channel-list/grid toggle created two guides. Make the grid the single main guide.
toggle='''            Chip(if (showGridGuide) "CHANNEL LIST" else "GRID GUIDE", showGridGuide) {
                showGridGuide = !showGridGuide
            }
'''
if toggle not in m: raise SystemExit("v4.56 guide toggle target missing")
m=m.replace(toggle,'''            Text("RYZOD GUIDE", color=ProgramCyan, fontSize=12.sp, fontWeight=FontWeight.ExtraBold)
''',1)
m=m.replace('var showGridGuide by remember { mutableStateOf(false) }','var showGridGuide by remember { mutableStateOf(true) }',1)
m=m.replace('onClose = { showGridGuide = false }','onClose = { }',1)

# Keep program actions on one visual level at the bottom of the dialog.
m=m.replace('''                    if (recordable) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(''','''                    if (recordable) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(''',1)

# Version marker.
m="// RYZOD_V456_VISUAL_UNIFICATION\n"+m
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 80',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.56"',g,count=1)
P.write_text(m); G.write_text(g)
print("Applied RYZOD 4.56 visual unification")
