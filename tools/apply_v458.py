#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
m=P.read_text(); g=G.read_text()
u=m.find("raw.githubusercontent.com/lukeypue/Easy-IPTV/main/latest.json")
if u < 0: raise SystemExit("updater manifest URL missing")
start=m.rfind("val raw = java.net.URL(",0,u)
if start < 0: start=m.rfind("val raw = java.net.URL(",u,u+400)
end=m.find("val obj = org.json.JSONObject(raw)",u)
if start < 0 or end < 0: raise SystemExit("updater fetch anchors missing")
line_start=m.rfind("\n",0,start)+1
indent=m[line_start:start]
start=line_start
end += len("val obj = org.json.JSONObject(raw)")
new='''// RYZOD_V458_UPDATER_RELIABILITY
val manifestUrl = "https://raw.githubusercontent.com/lukeypue/Easy-IPTV/main/latest.json?ts=" + System.currentTimeMillis()
val conn = (java.net.URL(manifestUrl).openConnection() as java.net.HttpURLConnection).apply {
    useCaches = false
    connectTimeout = 15_000
    readTimeout = 15_000
    setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
    setRequestProperty("Pragma", "no-cache")
    setRequestProperty("User-Agent", "RYZOD-Updater/" + BuildConfig.VERSION_NAME)
}
val raw = try {
    conn.connect()
    if (conn.responseCode !in 200..299) throw java.io.IOException("Update server returned " + conn.responseCode)
    conn.inputStream.bufferedReader().use { it.readText() }
} finally {
    conn.disconnect()
}
val obj = org.json.JSONObject(raw)'''
m=m[:start]+new+m[end:]
m=m.replace('updateStatus = "Checking…"','updateStatus = "Checking RYZOD update server…" ',1)
m=m.replace('updateStatus = "RYZOD $name is available."','updateStatus = "RYZOD $name is available (build $code)."',1)
m=m.replace('updateStatus = "You\'re up to date — RYZOD ${BuildConfig.VERSION_NAME}."','updateStatus = "You\'re up to date — RYZOD ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})."',1)
m=m.replace('updateStatus = "Couldn\'t check right now. Your saved downloads still work offline."','updateStatus = "Update check failed: " + (it.message ?: "network error")',1)
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 82',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.58"',g,count=1)
P.write_text(m); G.write_text(g)
print("Applied RYZOD 4.58 reliable no-cache updater")
