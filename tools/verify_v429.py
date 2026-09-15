from pathlib import Path

main = Path("app/src/main/java/com/easyiptv/player/MainActivity.kt").read_text(encoding="utf-8")
data = Path("app/src/main/java/com/easyiptv/player/Data.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

checks = {
    "version 4.29": 'versionName = "4.29"' in gradle and 'versionCode = 54' in gradle,
    "premium gradient": 'Brush.verticalGradient' in main and 'ElectricCyan' in main,
    "always-yellow search": 'width = if (fieldFocused) 4.dp else 2.dp' in main and 'color = Accent' in main,
    "universal metadata search": 'ZAKO_V429_UNIVERSAL_SEARCH' in main and 'it.searchMeta.contains(q' in main,
    "visible search movie download": 'contentDescription = "Download movie"' in main and 'DownloadStore.start(context, prefs, m.name, m.url)' in main,
    "movie metadata model": 'val searchMeta: String = ""' in data,
    "provider cast capture": 'o.optString("cast", "")' in data and 'o.optString("director", "")' in data,
    "cache metadata": '.put("sm", m.searchMeta)' in data and 'searchMeta = o.optString("sm", "")' in data,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Zako 4.29 verification failed: " + ", ".join(failed))
print("Zako 4.29 verification passed")
