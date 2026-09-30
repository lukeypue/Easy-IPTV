package com.easyiptv.player

import org.junit.Assert.*
import org.junit.Test

class GuideGeometryTest {
    private fun event(name: String, start: Long, end: Long) = EpgEntry(name, "", start, end)

    @Test fun twoHourFilmIsOneCell() {
        val film = event("Star Wars", 0, 120)
        val cells = GuideGeometry.cells(listOf(film), 0, 120)
        assertEquals(1, cells.size)
        assertEquals(film, cells.single().entry)
        assertEquals(120L, cells.single().endMs - cells.single().startMs)
    }
    @Test fun irregularStartsAlignToRulerAndFillGaps() {
        val cells = GuideGeometry.cells(listOf(event("A", -15, 45), event("B", 55, 150)), 0, 120)
        assertEquals(listOf(0L, 45L, 55L), cells.map { it.startMs })
        assertEquals(listOf(45L, 55L, 120L), cells.map { it.endMs })
        assertNull(cells[1].entry)
    }
    @Test fun duplicateProviderRowsDoNotRepeatOrOverlap() {
        val a = event("Film", 0, 120)
        assertEquals(1, GuideGeometry.cells(listOf(a, a, event("Other", 30, 90)), 0, 120).size)
    }
    @Test fun noInformationStillHasSelectableTimeCell() {
        val cell = GuideGeometry.cells(emptyList(), 0, 120).single()
        assertEquals(120L, cell.endMs)
        assertNull(cell.entry)
    }
}
