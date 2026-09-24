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

# Setup keyboard must overlay rather than extend the setup Column below the password field.
m=m.replace('''        if (editing) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center) {''','''        if (editing) {
            androidx.compose.ui.window.Dialog(onDismissRequest={ editing=false }) {
            Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center) {''',1)
# Close the Dialog in addition to the keyboard Box.
needle='''            }
            }
        }
    }
}'''
repl='''            }
            }
            }
        }
    }
}'''
if needle in m: m=m.replace(needle,repl,1)

# Remaining visible legacy brand text in generated UI only.
m=m.replace('Text("ZAKO",','Text("RYZOD",')
m=m.replace('"ZAKO"','"RYZOD"')

# Version.
m="// RYZOD_V457_FIRETV_CORRECTIONS\n"+m
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 81',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.57"',g,count=1)

P.write_text(m); G.write_text(g)
print("Applied RYZOD 4.57 Fire TV corrections")
