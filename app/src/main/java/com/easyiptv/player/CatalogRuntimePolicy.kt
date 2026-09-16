package com.easyiptv.player

// ZAKO_V447_CATALOG_RUNTIME
object CatalogRuntimePolicy {
    const val pageSize = 40
    const val searchDebounceMs = 250L
    const val livePrefetchPages = 1
    const val idlePrefetchPages = 2

    fun allowCatalogWork(livePlaying: Boolean, playerBuffering: Boolean): Boolean =
        !playerBuffering

    fun prefetchPages(livePlaying: Boolean, playerBuffering: Boolean): Int = when {
        playerBuffering -> 0
        livePlaying -> livePrefetchPages
        else -> idlePrefetchPages
    }

    fun allowArtworkPrefetch(livePlaying: Boolean, playerBuffering: Boolean): Boolean =
        !livePlaying && !playerBuffering
}
