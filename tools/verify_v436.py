from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text()
data = Path('app/src/main/java/com/easyiptv/player/Data.kt').read_text()
gradle = Path('app/build.gradle.kts').read_text()
stability_path = Path('app/src/main/java/com/easyiptv/player/StabilityCore.kt')
stability = stability_path.read_text() if stability_path.exists() else ''

checks = {
    'versionCode 61': 'versionCode = 61' in gradle,
    'versionName 4.36': 'versionName = "4.36"' in gradle,
    'stability core marker': 'ZAKO_V436_STABILITY_CORE' in stability,
    'crash diagnostics marker': 'ZAKO_V436_CRASH_DIAGNOSTICS' in stability,
    'resource supervisor marker': 'ZAKO_V436_RESOURCE_SUPERVISOR' in stability,
    'catalog sqlite marker': 'ZAKO_V436_CATALOG_SQLITE' in stability,
    'install stability core': 'StabilityCore.install(this)' in main,
    'trim memory hook': 'StabilityCore.onTrimMemory(level)' in main,
    'low memory hook': 'StabilityCore.onLowMemory()' in main,
    'background cancellation': 'BackgroundWorkSupervisor.cancelNonEssential()' in main,
    'catalog job supervision': 'BackgroundWorkSupervisor.replace("catalog", catalogJob)' in main,
    'disk catalog save': 'CatalogDiskIndex.get(context).replaceCatalog(key, data.movies, data.series)' in data,
    'disk movie load': 'CatalogDiskIndex.get(context).loadMovies(key)' in data,
    'disk series load': 'CatalogDiskIndex.get(context).loadSeries(key)' in data,
    'slim json cache marker': 'ZAKO_V436_SLIM_JSON_CACHE' in data,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('v4.36 verification failed: ' + ', '.join(failed))
print('v4.36 verification passed')
