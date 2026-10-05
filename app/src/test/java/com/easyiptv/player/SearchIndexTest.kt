package com.easyiptv.player

import org.junit.Assert.*
import org.junit.Test

class SearchIndexTest {
    @Test fun repeatedQueriesReusePreparedTitlesAndKeepExactMatchesFirst() {
        var reads=0
        val items=(1..5000).map { "Movie $it (2020)" }+"ABC 20/20"
        val index=SearchIndex(items,{reads++;it})
        assertEquals("ABC 20/20",index.find(TitleQuery("20/20")).first())
        assertEquals("ABC 20/20",index.find(TitleQuery("20 20")).first())
        assertEquals(items.size,reads)
        assertEquals(30,index.find(TitleQuery("m")).size)
    }
    @Test fun cancelledCatalogScanStopsWithoutPublishingPartialResults() {
        val index=SearchIndex((1..5000).toList(),{"Movie $it"})
        var checks=0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            index.find(TitleQuery("unavailable"),checkActive={if(++checks==3) throw java.util.concurrent.CancellationException()})
        }
        assertEquals(3,checks)
    }
    @Test fun futureGuideWindowsStopAtThreeDaysAndNeverBrowseThePast() {
        var page=0
        repeat(100) {page=GuideNavigation.page(page,1)}
        assertEquals(259_200_000L,(page+1)*GuideNavigation.WINDOW_MS)
        repeat(100) {page=GuideNavigation.page(page,-1)}
        assertEquals(0,page)
        assertEquals(1,GuideNavigation.row(0,1,10))
        assertEquals(9,GuideNavigation.row(9,1,10))
    }
    @Test fun manualDurationsProduceEveryHalfHourStopIncludingCrossingMidnight() {
        assertEquals(listOf(30,60,90,120,150,180,210,240,270,300),ManualRecordingDuration.minutes)
        assertEquals(91_800_000L,ManualRecordingDuration.end(86_400_000L,90))
        assertEquals(104_400_000L,ManualRecordingDuration.end(86_400_000L,300))
        assertThrows(IllegalArgumentException::class.java) {ManualRecordingDuration.end(0,0)}
    }
}
