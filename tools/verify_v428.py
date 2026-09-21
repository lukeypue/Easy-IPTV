from pathlib import Path

main = Path("app/src/main/java/com/easyiptv/player/MainActivity.kt").read_text(encoding="utf-8")
downloads = Path("app/src/main/java/com/easyiptv/player/Downloads.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

checks = {
    "version code 53": 'versionCode = 53' in gradle,
    "version name 4.28": 'versionName = "4.28"' in gradle,
    "v4.28 search debounce marker": "ZAKO_V428_SEARCH_DEBOUNCE" in main and "settledQuery" in main,
    "search loading overlay removed": 'section == "search"))' not in main,
    "yellow search field marker": "ZAKO_V428_YELLOW_SEARCH" in main,
    "movie details on OK marker": "ZAKO_V428_MOVIE_DETAILS" in main,
    "movie OK opens info": "infoMovie = m" in main and "OK opens details" in main,
    "download request hardening marker": "ZAKO_V428_DOWNLOAD_HEADERS" in downloads,
    "download byte range header": '.header("Range", "bytes=0-")' in downloads,
    "download identity encoding": '.header("Accept-Encoding", "identity")' in downloads,
    "v4.28 visible label": "Zako 4.28" in main,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    print("Zako 4.28 verification FAILED:")
    for name in failed:
        print(f" - {name}")
    raise SystemExit(1)

print("Zako 4.28 verification passed")
for name in checks:
    print(f" - {name}")
