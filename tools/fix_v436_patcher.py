from pathlib import Path

p = Path('tools/apply_v436.py')
t = p.read_text()

# v4.29 added searchMeta to generated cache rows; target that generated shape.
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

# Preserve search metadata in the SQLite sidecar.
t = t.replace(
    'url TEXT NOT NULL, PRIMARY KEY(k,id))',
    "url TEXT NOT NULL, search_meta TEXT NOT NULL DEFAULT '', PRIMARY KEY(k,id))",
)
t = t.replace(
    'cat TEXT, PRIMARY KEY(k,id))',
    "cat TEXT, search_meta TEXT NOT NULL DEFAULT '', PRIMARY KEY(k,id))",
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

# v4.30 removed the giant post-load cache save. After v4.36 slims DataCache,
# restore that save so the catalog reaches SQLite without recreating giant JSON.
old_comment = (
    '            // ZAKO_V430_LOW_MEMORY_CATALOG: skip serializing the giant merged VOD catalog\\n'
    '            // here. Re-encoding it duplicates the catalog in RAM at the worst possible moment.\\n'
)
new_comment = (
    '            // ZAKO_V436_SQLITE_CATALOG_SAVE: the JSON part is now slim; VOD rows go to SQLite.\\n'
    '            if (cacheKey != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\\n'
    '                DataCache.save(context, cacheKey, merged)\\n'
    '            }\\n'
)

append = (
    '\n# With the cache now slim, persist the freshly loaded catalog to SQLite on IO.\n'
    'main = once(main,\n'
    "'''" + old_comment.replace('\\n', '\n') + "''',\n"
    "'''" + new_comment.replace('\\n', '\n') + "''', 'restore slim catalog persistence')\n"
)
write_marker = 'MAIN.write_text(main)\nDATA.write_text(data)\nGRADLE.write_text(gradle)'
if write_marker not in t:
    raise SystemExit('v4.36 patcher write marker missing')
t = t.replace(write_marker, append + '\n' + write_marker, 1)

p.write_text(t)
print('aligned v4.36 patcher with generated v4.35 source')
