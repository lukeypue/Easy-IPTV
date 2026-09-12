from pathlib import Path

p = Path('tools/apply_v436.py')
t = p.read_text()

# v4.29 added searchMeta to the generated cache rows. Update the exact
# multiline patch targets in apply_v436.py so they match generated 4.35.
old_movie_save = '''                    JSONObject().put("i", m.id).put("n", m.name).put("ic", m.icon ?: "")
                        .put("c", m.categoryId ?: "").put("u", m.url)
'''
new_movie_save = '''                    JSONObject().put("i", m.id).put("n", m.name).put("ic", m.icon ?: "")
                        .put("c", m.categoryId ?: "").put("u", m.url).put("sm", m.searchMeta)
'''
old_series_save = '''                    JSONObject().put("i", s.id).put("n", s.name).put("ic", s.icon ?: "")
                        .put("c", s.categoryId ?: "")
'''
new_series_save = '''                    JSONObject().put("i", s.id).put("n", s.name).put("ic", s.icon ?: "")
                        .put("c", s.categoryId ?: "").put("sm", s.searchMeta)
'''
old_movie_load = '''                        categoryId = o.optString("c").ifBlank { null },
                        url = o.optString("u")
'''
new_movie_load = '''                        categoryId = o.optString("c").ifBlank { null },
                        url = o.optString("u"),
                        searchMeta = o.optString("sm", "")
'''
old_series_load = '''                        icon = o.optString("ic").ifBlank { null },
                        categoryId = o.optString("c").ifBlank { null }
                    )
'''
new_series_load = '''                        icon = o.optString("ic").ifBlank { null },
                        categoryId = o.optString("c").ifBlank { null },
                        searchMeta = o.optString("sm", "")
                    )
'''
for old, new, label in [
    (old_movie_save, new_movie_save, 'movie save target'),
    (old_series_save, new_series_save, 'series save target'),
    (old_movie_load, new_movie_load, 'movie load target'),
    (old_series_load, new_series_load, 'series load target'),
]:
    if old not in t:
        raise SystemExit(f'{label} missing in apply_v436.py')
    t = t.replace(old, new, 1)

# Preserve search metadata in the SQLite sidecar.
repls = [
    ('url TEXT NOT NULL, PRIMARY KEY(k,id))', 'url TEXT NOT NULL, search_meta TEXT NOT NULL DEFAULT \'\', PRIMARY KEY(k,id))'),
    ('cat TEXT, PRIMARY KEY(k,id))', 'cat TEXT, search_meta TEXT NOT NULL DEFAULT \'\', PRIMARY KEY(k,id))'),
    ('INSERT OR REPLACE INTO movies(k,id,name,icon,cat,url) VALUES(?,?,?,?,?,?)', 'INSERT OR REPLACE INTO movies(k,id,name,icon,cat,url,search_meta) VALUES(?,?,?,?,?,?,?)'),
    ('mv.bindString(6,m.url); mv.executeInsert()', 'mv.bindString(6,m.url); mv.bindString(7,m.searchMeta); mv.executeInsert()'),
    ('INSERT OR REPLACE INTO series(k,id,name,icon,cat) VALUES(?,?,?,?,?)', 'INSERT OR REPLACE INTO series(k,id,name,icon,cat,search_meta) VALUES(?,?,?,?,?,?)'),
    ('if (s.categoryId != null) sr.bindString(5,s.categoryId) else sr.bindNull(5)\n                sr.executeInsert()', 'if (s.categoryId != null) sr.bindString(5,s.categoryId) else sr.bindNull(5)\n                sr.bindString(6,s.searchMeta); sr.executeInsert()'),
    ('arrayOf("id","name","icon","cat","url")', 'arrayOf("id","name","icon","cat","url","search_meta")'),
    ('Movie(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))', 'Movie(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))'),
    ('arrayOf("id","name","icon","cat")', 'arrayOf("id","name","icon","cat","search_meta")'),
    ('SeriesItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3))', 'SeriesItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))'),
]
for old, new in repls:
    if old not in t:
        raise SystemExit(f'sqlite patch target missing: {old[:50]}')
    t = t.replace(old, new, 1)

# Never log provider URLs/credentials from arbitrary exception messages.
t = t.replace(
    'note("FATAL thread=${thread.name} type=${error.javaClass.simpleName} msg=${error.message ?: ""}")',
    'note("FATAL thread=${thread.name} type=${error.javaClass.simpleName}")',
    1,
)

# v4.25 already owns MainActivity.onTrimMemory(). Make v4.36 extend that exact
# lifecycle hook instead of generating a second overload that Kotlin rejects.
old_memory_patch = r"""main = once(main,
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
''', 'memory lifecycle')"""
new_memory_patch = r"""main = once(main,
'''    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Posters can consume a meaningful chunk of RAM on Fire TV. They are
            // disposable network/cache images, so drop only Coil's MEMORY cache
            // when Android says pressure is building. Disk cache and playback stay.
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
        }
    }

    override fun onDestroy() {
        Playback.releaseAll()
        super.onDestroy()
    }
''',
'''    override fun onTrimMemory(level: Int) {
        StabilityCore.onTrimMemory(level)
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Posters can consume a meaningful chunk of RAM on Fire TV. They are
            // disposable network/cache images, so drop only Coil's MEMORY cache
            // when Android says pressure is building. Disk cache and playback stay.
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
        }
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
''', 'memory lifecycle')"""
if old_memory_patch not in t:
    raise SystemExit('v4.36 memory lifecycle patch target missing')
t = t.replace(old_memory_patch, new_memory_patch, 1)

# v4.30 removed the giant catalog save call. Once DataCache.save becomes slim
# JSON + SQLite, restoring the save no longer creates the old JSON memory spike.
append = r"""

# With the cache now slim, persist the freshly loaded catalog to SQLite on IO.
main = once(main,
'''            // ZAKO_V430_LOW_MEMORY_CATALOG: skip serializing the giant merged VOD catalog
            // here. Re-encoding it duplicates the catalog in RAM at the worst possible moment.
''',
'''            // ZAKO_V436_SQLITE_CATALOG_SAVE: the JSON part is now slim; VOD rows go to SQLite.
            if (cacheKey != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                DataCache.save(context, cacheKey, merged)
            }
''', 'restore slim catalog persistence')
"""
write_marker = "MAIN.write_text(main)\nDATA.write_text(data)\nGRADLE.write_text(gradle)"
if write_marker not in t:
    raise SystemExit('v4.36 patcher write marker missing')
t = t.replace(write_marker, append + "\n" + write_marker, 1)

p.write_text(t)
print('aligned v4.36 patcher with generated v4.35 source')
