#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
S=Path("app/src/main/res/values/strings.xml")
m=P.read_text(); g=G.read_text(); s=S.read_text()
m=m.replace("Zako","RYZOD")
# Lightweight presentation polish: vector-drawn brand mark + subtle grid. No bitmap allocation.
brand = r'''
@Composable
private fun RyzodBrandMark(compact: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(if (compact) 27.dp else 38.dp)) {
            val w=size.width; val h=size.height
            drawCircle(Color(0x3033E1FF), radius=w*0.47f)
            drawCircle(Color(0xFF33E1FF), radius=w*0.43f, style=androidx.compose.ui.graphics.drawscope.Stroke(width=(w*0.055f).coerceAtLeast(1f)))
            val sw=(w*0.105f).coerceAtLeast(2f)
            drawLine(Color(0xFFDDF8FF), Offset(w*.25f,h*.18f), Offset(w*.25f,h*.82f), sw, StrokeCap.Round)
            drawLine(Color(0xFFDDF8FF), Offset(w*.25f,h*.18f), Offset(w*.62f,h*.18f), sw, StrokeCap.Round)
            drawLine(Color(0xFFDDF8FF), Offset(w*.62f,h*.18f), Offset(w*.62f,h*.48f), sw, StrokeCap.Round)
            drawLine(Color(0xFFDDF8FF), Offset(w*.25f,h*.49f), Offset(w*.59f,h*.49f), sw, StrokeCap.Round)
            drawLine(Color(0xFFDDF8FF), Offset(w*.48f,h*.49f), Offset(w*.75f,h*.82f), sw, StrokeCap.Round)
            drawLine(Color(0xFF33E1FF), Offset(w*.70f,h*.34f), Offset(w*.70f,h*.64f), sw*.7f, StrokeCap.Round)
            drawLine(Color(0xFF33E1FF), Offset(w*.70f,h*.34f), Offset(w*.88f,h*.49f), sw*.7f, StrokeCap.Round)
            drawLine(Color(0xFF33E1FF), Offset(w*.88f,h*.49f), Offset(w*.70f,h*.64f), sw*.7f, StrokeCap.Round)
        }
        Spacer(Modifier.width(if (compact) 7.dp else 10.dp))
        Text("RYZOD", fontWeight=FontWeight.ExtraBold, fontSize=if (compact) 17.sp else 19.sp, color=Color(0xFFDDF8FF), letterSpacing=1.2.sp)
    }
}

@Composable
private fun RyzodGridBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 54.dp.toPx()
        var x=0f
        while(x<=size.width){ drawLine(Color(0x102F6BFF),Offset(x,0f),Offset(x,size.height),1f); x+=step }
        var y=0f
        while(y<=size.height){ drawLine(Color(0x102F6BFF),Offset(0f,y),Offset(size.width,y),1f); y+=step }
    }
}
'''
val classNeedle = "class MainActivity : ComponentActivity() {"
if (classNeedle !in m): raise SystemExit("v4.55 brand insertion target missing")
m=m.replace(classNeedle, brand+"\\n"+classNeedle,1)
createNeedle='''        super.onCreate(savedInstanceState)
        setContent {'''
createNew='''        super.onCreate(savedInstanceState)
        // RYZOD low-memory profile: Fire TV sticks have tight heaps and poster/logo
        // browsing can otherwise let Coil's default memory cache grow too aggressively.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .memoryCache {
                    coil.memory.MemoryCache.Builder(this)
                        .maxSizePercent(0.06)
                        .build()
                }
                .crossfade(false)
                .build()
        )
        setContent {'''
if createNeedle not in m: raise SystemExit("v4.55 low-memory image-cache target missing")
m=m.replace(createNeedle,createNew,1)
surfaceNeedle='''                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    App()
                }'''
surfaceNew='''                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    Box(Modifier.fillMaxSize()) {
                        RyzodGridBackground()
                        App()
                    }
                }'''
if surfaceNeedle not in m: raise SystemExit("v4.55 grid target missing")
m=m.replace(surfaceNeedle,surfaceNew,1)
m=m.replace('Text("RYZOD", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Ink)','RyzodBrandMark(compact = false)',1)
m=m.replace('Text("RYZOD", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)','RyzodBrandMark(compact = true)',1)
s=s.replace(">Zako<",">RYZOD<")
old='''        val oldRing = ring
        ring = null
        runCatching { oldRing?.close() }
        bytesWritten = 0L'''
new='''        val oldRing = ring
        ring = null
        if (oldRing != null) Thread({ runCatching { oldRing.close() } }, "timeshift-cleanup").apply { isDaemon = true }.start()
        bytesWritten = 0L'''
if old not in m: raise SystemExit("v4.55 rolling-ring cleanup target missing")
m=m.replace(old,new,1)
m="// RYZOD_V455_VERIFIED_FULL\n"+m
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 79',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.55"',g,count=1)
P.write_text(m); G.write_text(g); S.write_text(s)
print("Applied RYZOD 4.55 branding and non-blocking timeshift cleanup")
