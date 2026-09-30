package com.easyiptv.player

import java.text.Normalizer
import java.util.Locale

/** Query work is cached once per settled search; exact punctuation always wins. */
internal class TitleQuery(raw: String) {
    private val literal = raw.trim().lowercase(Locale.ROOT)
    private val compact = fold(literal)
    private val words = words(literal)

    fun <T> find(items: List<T>, title: (T) -> String, metadata: (T) -> String = { "" }, limit: Int = 30): List<T> =
        items.mapNotNull { item ->
            val score = rank(title(item)) ?: rank(metadata(item))?.plus(10)
            score?.let { it to item }
        }.sortedBy { it.first }.take(limit).map { it.second }

    fun matches(text: String): Boolean = rank(text) != null

    fun rank(text: String): Int? {
        if (literal.isEmpty()) return null
        if (text.lowercase(Locale.ROOT).contains(literal)) return 0
        if (compact.isEmpty()) return null
        if (words.isNotEmpty() && (" " + words(text) + " ").contains(" $words ")) return 1
        val candidate = fold(text)
        return when {
            candidate == compact -> 2
            candidate.contains(compact) -> 3
            else -> null
        }
    }

    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun words(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).replace(marks, "")
        .replace(separators, " ").trim()

    companion object {
        private val marks = Regex("\\p{M}+")
        private val separators = Regex("[^\\p{L}\\p{N}]+")
    }
}

/** One layout supplies Search, login and playlist entry on phone and TV. */
internal object KeyboardLayout {
    fun rows(page: Int, upper: Boolean): List<List<String>> {
        val characters = when (page) {
            1 -> listOf("1234567890", "@#\$%&*()-+", "/\\:;\"'!?=", "._,[]{}<>|")
            2 -> listOf("1234567890", "~`^€£¥¢§©®", "±×÷°•…¡¿«»", "{}_[]<>|\\")
            else -> listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
        }.map { line -> line.map { c -> if (upper && page == 0) c.uppercaseChar().toString() else c.toString() } }
        return listOf(listOf("ABC", "!?#", "+=<>")) + characters +
            listOf(listOf("SHIFT", "SPACE", "DELETE", "CLEAR", "DONE"))
    }

    fun move(rows: List<List<String>>, row: Int, col: Int, dx: Int, dy: Int): Pair<Int, Int> {
        val nextRow = (row + dy + rows.size) % rows.size
        val nextCol = if (dy != 0) col.coerceAtMost(rows[nextRow].lastIndex)
            else (col + dx + rows[nextRow].size) % rows[nextRow].size
        return nextRow to nextCol
    }

    fun edit(value: String, key: String): String = when (key) {
        "SPACE" -> value + " "
        "DELETE" -> value.dropLast(1)
        "CLEAR" -> ""
        else -> value + key
    }
}
