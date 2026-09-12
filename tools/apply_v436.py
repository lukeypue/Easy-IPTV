from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
DATA = Path('app/src/main/java/com/easyiptv/player/Data.kt')
GRADLE = Path('app/build.gradle.kts')
STABILITY = Path('app/src/main/java/com/easyiptv/player/StabilityCore.kt')

main = MAIN.read_text()
data = DATA.read_text()
gradle = GRADLE.read_text()

def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1, found {n}')
    return text.replace(old, new, 1)

gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 61', gradle, count=1)
if n != 1: raise SystemExit('version code')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.36"', gradle, count=1)
if n != 1: raise SystemExit('version name')

stability = r'''package com.easyiptv.player

import android.app.ActivityManager
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Debug
import kotlinx.coroutines.Job
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ZAKO_V436_STABILITY_CORE: bounded process diagnostics and resource ownership.
object StabilityCore {
    private const val MAX_LOG_BYTES = 192 * 1024
    private var appContext: Context? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    @Volatile private var lastScreen = "startup"

    @Synchronized
    fun install(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        // ZAKO_V436_CRASH_DIAGNOSTICS: persist the last useful state before Android exits.
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            note("FATAL thread=${thread.name} type=${error.javaClass.simpleName} msg=${error.message ?: ""}")
            previousHandler?.uncaughtException(thread, error)
        }
        note("process_start")
    }

    fun noteScreen(name: String) {
        lastScreen = name.take(80)
        note("screen=$lastScreen")
    }

    fun note(event: String) {
        val context = appContext ?: return
        runCatching {
            val rt = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val maxMb = rt.maxMemory() / (1024 * 1024)
            val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "$stamp | $event | screen=$lastScreen | java=${usedMb}/${maxMb}MB native=${nativeMb}MB\n"
            val f = File(context.filesDir, "zako_stability.log")
            if (f.exists() && f.length() > MAX_LOG_BYTES) {
                val keep = f.readText().takeLast(MAX_LOG_BYTES / 2)
                f.writeText("--- rolling stability log ---\n$keep")
            }
            f.appendText(line)
        }
    }

    fun onTrimMemory(level: Int) {
        note("trim_memory level=$level")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            BackgroundWorkSupervisor.cancelNonEssential()
        }
    }

    fun onLowMemory() {
        note("low_memory")
        BackgroundWorkSupervisor.cancelNonEssential()
    }

    fun beforeBackground() {
        note("background")
        BackgroundWorkSupervisor.cancelNonEssential()
    }

    fun shutdown() {
        note("activity_destroy")
        BackgroundWorkSupervisor.cancelAll()
    }
}

// ZAKO_V436_RESOURCE_SUPERVISOR: only one job per expensive background purpose.
object BackgroundWorkSupervisor {
    private val jobs = ConcurrentHashMap<String, Job>()

    fun replace(tag: String, job: Job) {
        val old = jobs.put(tag, job)
        if (old !== job) old?.cancel()
        job.invokeOnCompletion { jobs.remove(tag, job) }
    }

    fun cancel(tag: String) { jobs.remove(tag)?.cancel() }
    fun cancelNonEssential() { cancel("catalog") }
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}

// ZAKO_V436_CATALOG_SQLITE: compact disk sidecar for giant VOD/Series catalogs.
class CatalogDiskIndex private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "zako_catalog.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE movies (k TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, icon TEXT, cat TEXT, url TEXT NOT NULL, PRIMARY KEY(k,id))")
        db.execSQL("CREATE INDEX movies_k_cat ON movies(k,cat)")
        db.execSQL("CREATE TABLE series (k TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, icon TEXT, cat TEXT, PRIMARY KEY(k,id))")
        db.execSQL("CREATE INDEX series_k_cat ON series(k,cat)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS movies")
        db.execSQL("DROP TABLE IF EXISTS series")
        onCreate(db)
    }

    fun replaceCatalog(key: String, movies: List<Movie>, series: List<SeriesItem>) = runCatching {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("movies", "k=?", arrayOf(key))
            writableDatabase.delete("series", "k=?", arrayOf(key))
            val mv = writableDatabase.compileStatement("INSERT OR REPLACE INTO movies(k,id,name,icon,cat,url) VALUES(?,?,?,?,?,?)")
            movies.forEach { m ->
                mv.clearBindings(); mv.bindString(1,key); mv.bindString(2,m.id); mv.bindString(3,m.name)
                if (m.icon != null) mv.bindString(4,m.icon) else mv.bindNull(4)
                if (m.categoryId != null) mv.bindString(5,m.categoryId) else mv.bindNull(5)
                mv.bindString(6,m.url); mv.executeInsert()
            }
            val sr = writableDatabase.compileStatement("INSERT OR REPLACE INTO series(k,id,name,icon,cat) VALUES(?,?,?,?,?)")
            series.forEach { s ->
                sr.clearBindings(); sr.bindString(1,key); sr.bindString(2,s.id); sr.bindString(3,s.name)
                if (s.icon != null) sr.bindString(4,s.icon) else sr.bindNull(4)
                if (s.categoryId != null) sr.bindString(5,s.categoryId) else sr.bindNull(5)
                sr.executeInsert()
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun loadMovies(key: String): List<Movie> = runCatching {
        readableDatabase.query("movies", arrayOf("id","name","icon","cat","url"), "k=?", arrayOf(key), null, null, "name COLLATE NOCASE").use { c ->
            buildList {
                while (c.moveToNext()) add(Movie(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4)))
            }
        }
    }.getOrDefault(emptyList())

    fun loadSeries(key: String): List<SeriesItem> = runCatching {
        readableDatabase.query("series", arrayOf("id","name","icon","cat"), "k=?", arrayOf(key), null, null, "name COLLATE NOCASE").use { c ->
            buildList {
                while (c.moveToNext()) add(SeriesItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3)))
            }
        }
    }.getOrDefault(emptyList())

    fun clear(key: String) = runCatching {
        writableDatabase.delete("movies", "k=?", arrayOf(key))
        writableDatabase.delete("series", "k=?", arrayOf(key))
    }

    companion object {
        @Volatile private var instance: CatalogDiskIndex? = null
        fun get(context: Context): CatalogDiskIndex = instance ?: synchronized(this) {
            instance ?: CatalogDiskIndex(context).also { instance = it }
        }
    }
}
'''
STABILITY.write_text(stability)

main = once(main,
'''    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
''',
'''    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StabilityCore.install(this)
        StabilityCore.note("activity_create")
        setContent {
''', 'install lifecycle')

main = once(main,
'''    override fun onStop() {
        // Fire TV storage + provider safety: when Zako is hidden, stop the live
''',
'''    override fun onStop() {
        StabilityCore.beforeBackground()
        BackgroundWorkSupervisor.cancelNonEssential()
        // Fire TV storage + provider safety: when Zako is hidden, stop the live
''', 'background lifecycle')

main = once(main,
'''    override fun onDestroy() {
        Playback.releaseAll()
        super.onDestroy()
    }
''',
'''    override fun onTrimMemory(level: Int) {
        StabilityCore.onTrimMemory(level)
        super.onTrimMemory(level)
    }

    override fun onLowMemory() {
        StabilityCore.onLowMemory()
        super.onLowMemory()
    }

    override fun onDestroy() {
        Playback.releaseAll()
        StabilityCore.shutdown()
        super.onDestroy()
    }
''', 'memory lifecycle')

main = once(main,
'''    LaunchedEffect(railSection, source, activeIdx, data, catalogLoadedThisSession, catalogRetry) {
        val s = source ?: return@LaunchedEffect
''',
'''    LaunchedEffect(railSection, source, activeIdx, data, catalogLoadedThisSession, catalogRetry) {
        val catalogJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        if (catalogJob != null) BackgroundWorkSupervisor.replace("catalog", catalogJob)
        val s = source ?: return@LaunchedEffect
''', 'catalog supervision')

main = once(main,
'''    // One-time "external drive found — use it?" prompt. Shows only if a drive is
''',
'''    LaunchedEffect(railSection) {
        StabilityCore.noteScreen(railSection)
    }

    // One-time "external drive found — use it?" prompt. Shows only if a drive is
''', 'screen diagnostics')

# Keep the startup JSON cache lightweight: live/categories remain there; giant VOD lists live in SQLite.
data = once(data,
'''            val movArr = JSONArray()
            data.movies.forEach { m ->
                movArr.put(
                    JSONObject().put("i", m.id).put("n", m.name).put("ic", m.icon ?: "")
                        .put("c", m.categoryId ?: "").put("u", m.url)
                )
            }
            root.put("movies", movArr)
            val serArr = JSONArray()
            data.series.forEach { s ->
                serArr.put(
                    JSONObject().put("i", s.id).put("n", s.name).put("ic", s.icon ?: "")
                        .put("c", s.categoryId ?: "")
                )
            }
            root.put("series", serArr)
''',
'''            // ZAKO_V436_SLIM_JSON_CACHE: VOD/Series rows are persisted once in SQLite,
            // not duplicated into one giant JSON cache as well.
            CatalogDiskIndex.get(context).replaceCatalog(key, data.movies, data.series)
''', 'slim cache save')

data = once(data,
'''            val movies = root.optJSONArray("movies")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Movie(
                        id = o.optString("i"), name = o.optString("n"),
                        icon = o.optString("ic").ifBlank { null },
                        categoryId = o.optString("c").ifBlank { null },
                        url = o.optString("u")
                    )
                }
            } ?: emptyList()
            val series = root.optJSONArray("series")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    SeriesItem(
                        id = o.optString("i"), name = o.optString("n"),
                        icon = o.optString("ic").ifBlank { null },
                        categoryId = o.optString("c").ifBlank { null }
                    )
                }
            } ?: emptyList()
''',
'''            val movies = CatalogDiskIndex.get(context).loadMovies(key)
            val series = CatalogDiskIndex.get(context).loadSeries(key)
''', 'disk cache load')

MAIN.write_text(main)
DATA.write_text(data)
GRADLE.write_text(gradle)
print('applied v4.36 stability core')
