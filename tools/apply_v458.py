#!/usr/bin/env python3
from pathlib import Path
import re
P=Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
G=Path("app/build.gradle.kts")
m=P.read_text(); g=G.read_text()
pattern=r'''val raw = java\.net\.URL\(\s*"https://raw\.githubusercontent\.com/lukeypue/Easy-IPTV/main/latest\.json"\s*\)\.readText\(\)\s*val obj = org\.json\.JSONObject\(raw\)'''
# v4.55 branding changes the generated User-Agent/string literals from Zako to RYZOD,
# but the manifest URL itself stays stable. If formatting/branding changed around the
# fetch, replace the smallest stable URL-read expression instead.
if not re.search(pattern,m):
    pattern=r'''val raw = java\.net\.URL\(\s*"https://raw\.githubusercontent\.com/lukeypue/Easy-IPTV/main/latest\.json"\s*\)\.readText\(\)'''
new='''                            // RYZOD_V458_UPDATER_RELIABILITY
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
m,n=re.subn(pattern,new if "JSONObject(raw)" in re.search(pattern,m).group(0) else new.rsplit("val obj = org.json.JSONObject(raw)",1)[0],m,count=1)
if n == 1 and "val obj = org.json.JSONObject(raw)" not in m[m.find("RYZOD_V458_UPDATER_RELIABILITY"):m.find("RYZOD_V458_UPDATER_RELIABILITY")+1800]:
    anchor=m.find("RYZOD_V458_UPDATER_RELIABILITY")
    end=m.find("                            Triple(",anchor)
    m=m[:end]+'                            val obj = org.json.JSONObject(raw)\n'+m[end:]
if n != 1: raise SystemExit("updater fetch block missing")
m=m.replace('updateStatus = "Checking…"','updateStatus = "Checking RYZOD update server…" ',1)
m=m.replace('updateStatus = "Zako $name is available."','updateStatus = "RYZOD $name is available (build $code)."',1)
m=m.replace('updateStatus = "You\'re up to date — Zako ${BuildConfig.VERSION_NAME}."','updateStatus = "You\'re up to date — RYZOD ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})."',1)
m=m.replace('updateStatus = "Couldn\'t check right now. Your saved downloads still work offline."','updateStatus = "Update check failed: " + (it.message ?: "network error")',1)
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 82',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "4.58"',g,count=1)
P.write_text(m); G.write_text(g)
print("Applied RYZOD 4.58 reliable no-cache updater")
