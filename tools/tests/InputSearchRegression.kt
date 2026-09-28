package com.easyiptv.player

fun main() {
    var assertions = 0
    fun expect(ok: Boolean, message: String) { check(ok) { message }; assertions++ }
    for (query in listOf("20/20", "20 20", "20-20", "2020")) {
        expect(TitleQuery(query).matches("ABC 20/20 (2026)"), "Missing 20/20 for $query")
        expect(TitleQuery(query).matches("ABC 20 20"), "Missing spaced title for $query")
    }
    expect(TitleQuery("law order").matches("Law & Order: SVU"), "Punctuation words")
    expect(TitleQuery("cafe").matches("Café Society"), "Accented title")
    expect(TitleQuery("20/20").matches("20／20"), "Unicode slash")
    expect(!TitleQuery("20/20").matches("News at 10"), "Unrelated result")
    expect(!TitleQuery(" ").matches("Anything"), "Blank query")
    expect(!TitleQuery("///").matches("Anything"), "Empty normalized query")
    expect(TitleQuery("///").matches("AC /// DC"), "Literal punctuation still works")
    expect(!TitleQuery("star wars").matches("Star Trek"), "Missing word")
    expect(TitleQuery("ALIEN").matches("Alien: Romulus"), "Case insensitive")
    val crowded = (1..40).map { "Documentary $it (2020)" } + "ABC 20/20"
    expect(TitleQuery("20/20").find(crowded, { it }).first() == "ABC 20/20", "Exact show hidden by 30 year matches")
    expect(TitleQuery("20 20").find(crowded, { it }).first() == "ABC 20/20", "Spaced query ranks the show before years")
    val metadata = listOf("Unrelated show" to "20/20 cast", "ABC 20/20" to "")
    expect(TitleQuery("20/20").find(metadata, { it.first }, { it.second }).first().first == "ABC 20/20", "Metadata hid a title match")

    // Removing a symbols page or a key must fail the printable ASCII contract.
    val typed = (0..2).flatMap { KeyboardLayout.rows(it, false).flatten() }.filter { it.length == 1 }.toSet()
    for (c in '!'..'~') {
        if (!c.isUpperCase()) expect(c.toString() in typed, "Cannot type ASCII character $c")
    }
    val normal = KeyboardLayout.rows(0, false)
    expect(KeyboardLayout.move(normal, 1, 9, 1, 0) == (1 to 0), "Right wrap")
    expect(KeyboardLayout.move(normal, 1, 0, -1, 0) == (1 to 9), "Left wrap")
    expect(KeyboardLayout.move(normal, 0, 2, 0, -1) == (5 to 2), "Up wrap")
    expect(KeyboardLayout.move(normal, 2, 9, 0, 1) == (3 to 8), "Short-row clamp")
    var value = ""
    for (key in listOf("2", "0", "/", "2", "0")) value = KeyboardLayout.edit(value, key)
    expect(value == "20/20", "20/20 entry")
    value = KeyboardLayout.edit(value, "DELETE")
    expect(value == "20/2", "Delete")
    expect(KeyboardLayout.edit(value, "CLEAR") == "", "Clear")
    expect(KeyboardLayout.edit("https:", "/") == "https:/", "URL punctuation unchanged")
    expect(KeyboardLayout.edit("", "DELETE") == "", "Delete empty")
    println("PASS: $assertions keyboard and search behavior assertions")
}
