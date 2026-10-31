package com.easyiptv.player

import java.lang.ref.WeakReference

/** One cheap result snapshot per playlist; heavy indexes may be reclaimed during video. */
class SearchSession {
    private var catalog: AppData? = null
    private var index: WeakReference<SearchCatalogIndex>? = null
    private var matches: SearchMatches? = null
    private var byEpgId: Map<String, LiveChannel> = emptyMap()
    private var byNorm: Map<String, LiveChannel> = emptyMap()
    private var guideRevision = -1
    private var guideIndex: WeakReference<SearchIndex<EpgStore.GuideHit>>? = null
    internal var guideMatches: Triple<String, Int, List<EpgStore.GuideHit>>? = null
    internal var scrollQuery = ""
    internal var scrollIndex = 0
    internal var scrollOffset = 0

    internal fun hasCatalog(data: AppData) = catalog === data
    internal fun indexFor(data: AppData) = index?.get().takeIf { catalog === data }
    internal fun rememberIndex(data: AppData, value: SearchCatalogIndex) {
        if (catalog !== data) { matches = null; scrollQuery = "" }
        catalog = data
        index = WeakReference(value)
        byEpgId = value.byEpgId
        byNorm = value.byNorm
    }
    @Suppress("UNUSED_PARAMETER")
    internal fun matchesFor(data: AppData, query: String, value: SearchCatalogIndex?) =
        matches?.takeIf { catalog === data && it.query == query && it.indexIdentity === data }
    internal fun rememberMatches(data: AppData, value: SearchMatches) {
        if (catalog === data && value.indexIdentity === data) matches = value
    }
    internal fun byEpgIdFor(data: AppData) = if(catalog === data) byEpgId else emptyMap()
    internal fun byNormFor(data: AppData) = if(catalog === data) byNorm else emptyMap()
    internal fun guideIndexFor(revision: Int) = guideIndex?.get().takeIf { guideRevision == revision }
    internal fun rememberGuideIndex(revision: Int, value: SearchIndex<EpgStore.GuideHit>) {
        if(guideRevision != revision) guideMatches = null
        guideRevision = revision
        guideIndex = WeakReference(value)
    }
    internal fun guideMatchesFor(query: String, revision: Int) =
        guideMatches?.takeIf { it.first == query && it.second == revision }
}
