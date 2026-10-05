package com.easyiptv.player

import java.text.Normalizer
import java.util.Locale

/** Prepared once per catalog revision, never repeatedly on the UI thread. */
internal class SearchText(text: String) {
    val literal = text.lowercase(Locale.ROOT)
    private val normalized = Normalizer.normalize(literal, Normalizer.Form.NFKD)
    val compact = normalized.filter { it.isLetterOrDigit() }
    val words = normalized.replace(marks, "").replace(separators, " ").trim()
    companion object {
        private val marks = Regex("\\p{M}+")
        private val separators = Regex("[^\\p{L}\\p{N}]+")
    }
}

internal class TitleQuery(raw: String) {
    private val query = SearchText(raw.trim())
    fun <T> find(items: List<T>, title: (T) -> String, metadata: (T) -> String = { "" }, limit: Int = 30): List<T> =
        SearchIndex(items, title, metadata).find(this, limit)
    fun matches(text: String): Boolean = rank(text) != null
    fun rank(text: String): Int? = rank(SearchText(text))
    fun rank(text: SearchText): Int? {
        if (query.literal.isEmpty()) return null
        if (text.literal.contains(query.literal)) return 0
        if (query.compact.isEmpty()) return null
        if (query.words.isNotEmpty() && (" " + text.words + " ").contains(" ${query.words} ")) return 1
        return when {
            text.compact == query.compact -> 2
            text.compact.contains(query.compact) -> 3
            else -> null
        }
    }
}

/** Bounded ranking buckets avoid allocating/sorting thousands of matches. */
internal class SearchIndex<T>(items: List<T>, title: (T) -> String,
    metadata: (T) -> String = { "" }, checkActive: () -> Unit = {}) {
    private class Entry<T>(val item: T, val title: SearchText, val metadata: SearchText?)
    private val entries = items.mapIndexed { i, item ->
        if (i % 128 == 0) checkActive()
        Entry(item, SearchText(title(item)), metadata(item).takeIf { it.isNotEmpty() }?.let(::SearchText))
    }
    fun find(query: TitleQuery, limit: Int = 30, checkActive: () -> Unit = {}, include: (T) -> Boolean = { true }): List<T> {
        if (limit <= 0) return emptyList()
        val buckets = Array(14) { ArrayList<T>() }
        for ((i, entry) in entries.withIndex()) {
            if (i % 128 == 0) checkActive()
            if (!include(entry.item)) continue
            val rank = query.rank(entry.title) ?: entry.metadata?.let { query.rank(it)?.plus(10) } ?: continue
            if (buckets[rank].size < limit) buckets[rank].add(entry.item)
            if (buckets[0].size == limit) break
        }
        checkActive()
        return buckets.asSequence().flatten().take(limit).toList()
    }
}

/** The same three reference pages serve every RYZOD input field. */
internal object KeyboardLayout {
    fun rows(page: Int, upper: Boolean): List<List<String>> {
        val characters = when (page) {
            1 -> listOf("~`·™©°¢®«»", "&*\"'=_()[]", ":;^/|\\{}<>", "+-#\$%?¿¡£€")
            2 -> listOf("àáâãäåæçćĉ", "èéêëìíîïĳĥ", "ñńòóôõöœøş", "śšßþùúûüỳý", "ÿð\"'.@!%()")
            else -> listOf("1234567890", "abcdefghij", "klmnopqrst", "uvwxyz!,.@")
        }.map { line -> line.map { c -> if (upper) c.uppercaseChar().toString() else c.toString() } }
        return characters + listOf(listOf("SHIFT", if (page == 0) "#$%" else "abc",
            if (page == 2) "#$%" else "áçé", "SPACE", "DELETE", "CLEAR"), listOf("PREVIOUS", "NEXT", "DONE"))
    }
    fun move(rows: List<List<String>>, row: Int, col: Int, dx: Int, dy: Int): Pair<Int, Int> {
        val nextRow = (row + dy + rows.size) % rows.size
        val nextCol = if (dy != 0) col.coerceAtMost(rows[nextRow].lastIndex)
            else (col + dx + rows[nextRow].size) % rows[nextRow].size
        return nextRow to nextCol
    }
    fun edit(value: String, key: String): String = when (key) {
        "SPACE" -> value + " "
        "DELETE" -> if (value.isEmpty()) value else value.substring(0, value.offsetByCodePoints(value.length, -1))
        "CLEAR" -> ""
        else -> value + key
    }
}
