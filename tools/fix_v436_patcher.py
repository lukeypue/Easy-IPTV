from pathlib import Path

p = Path('tools/apply_v436.py')
t = p.read_text()

# v4.29 added searchMeta to the generated cache rows, so v4.36 must target
# that exact generated 4.35 shape rather than the older base-source shape.
t = t.replace(
    '.put("c", m.categoryId ?: "").put("u", m.url)\\n',
    '.put("c", m.categoryId ?: "").put("u", m.url).put("sm", m.searchMeta)\\n',
    1,
)
t = t.replace(
    '.put("c", s.categoryId ?: "")\\n',
    '.put("c", s.categoryId ?: "").put("sm", s.searchMeta)\\n',
    1,
)

# Preserve search metadata in the SQLite sidecar too.
t = t.replace(
    'url TEXT NOT NULL, PRIMARY KEY(k,id))',
    'url TEXT NOT NULL, search_meta TEXT NOT NULL DEFAULT \'\', PRIMARY KEY(k,id))',
)
t = t.replace(
    'cat TEXT, PRIMARY KEY(k,id))',
    'cat TEXT, search_meta TEXT NOT NULL DEFAULT \'\', PRIMARY KEY(k,id))',
)
t = t.replace(
    'INSERT OR REPLACE INTO movies(k,id,name,icon,cat,url) VALUES(?,?,?,?,?,?)',
    'INSERT OR REPLACE INTO movies(k,id,name,icon,cat,url,search_meta) VALUES(?,?,?,?,?,?,?)',
)
t = t.replace(
    'mv.bindString(6,m.url); mv.executeInsert()',
    'mv.bindString(6,m.url); mv.bindString(7,m.searchMeta); mv.executeInsert()',
)
t = t.replace(
    'INSERT OR REPLACE INTO series(k,id,name,icon,cat) VALUES(?,?,?,?,?)',
    'INSERT OR REPLACE INTO series(k,id,name,icon,cat,search_meta) VALUES(?,?,?,?,?,?)',
)
t = t.replace(
    'if (s.categoryId != null) sr.bindString(5,s.categoryId) else sr.bindNull(5)\\n                sr.executeInsert()',
    'if (s.categoryId != null) sr.bindString(5,s.categoryId) else sr.bindNull(5)\\n                sr.bindString(6,s.searchMeta); sr.executeInsert()',
)
t = t.replace(
    'arrayOf("id","name","icon","cat","url")',
    'arrayOf("id","name","icon","cat","url","search_meta")',
)
t = t.replace(
    'Movie(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))',
    'Movie(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))',
)
t = t.replace(
    'arrayOf("id","name","icon","cat")',
    'arrayOf("id","name","icon","cat","search_meta")',
)
t = t.replace(
    'SeriesItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3))',
    'SeriesItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))',
)

# v4.30 intentionally removed the giant JSON save after catalog loading. 4.36
# restores that call only after DataCache.save has been converted to slim JSON
# + SQLite, so it no longer recreates the old memory spike.
needle = "'''            // ZAKO_V430_LOW_MEMORY_CATALOG: skip serializing the giant merged VOD catalog\\n            // here. Re-encoding it duplicates the catalog in RAM at the worst possible moment.\\n''',"
replacement = "'''            // ZAKO_V430_LOW_MEMORY_CATALOG: skip serializing the giant merged VOD catalog\\n            // here. Re-encoding it duplicates the catalog in RAM at the worst possible moment.\\n''',"
# The main patch is inserted after the DataCache transformation using a direct
# generated-source replacement appended to apply_v436.py.
append = r'''

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
'''
# Insert before final writes so the modified main is actually written.
write_marker = "MAIN.write_text(main)\\nDATA.write_text(data)\\nGRADLE.write_text(gradle)"
if write_marker not in t:
    raise SystemExit('v4.36 patcher write marker missing')
t = t.replace(write_marker, append + "\\n" + write_marker, 1)

p.write_text(t)
print('aligned v4.36 patcher with generated v4.35 source')
