package com.easyiptv.player

import java.util.Locale

/** All catalog-sized preparation shares one cancellable background build. */
internal class SearchCatalogIndex(data: AppData, checkActive: () -> Unit) {
    val live = SearchIndex(data.live, { it.name }, checkActive = checkActive)
    val movies = SearchIndex(data.movies, { it.name }, { it.searchMeta }, checkActive)
    val series = SearchIndex(data.series, { it.name }, { it.searchMeta }, checkActive)
    val byEpgId: Map<String, LiveChannel>
    val byNorm: Map<String, LiveChannel>
    init {
        val epg = HashMap<String, LiveChannel>()
        val names = HashMap<String, LiveChannel>()
        val separators = Regex("[^a-z0-9]")
        data.live.forEachIndexed { i, channel ->
            if (i % 128 == 0) checkActive()
            channel.epgId?.let { epg[it.lowercase(Locale.ROOT)] = channel }
            names[channel.name.lowercase(Locale.ROOT).replace(separators, "")] = channel
        }
        checkActive()
        byEpgId = epg
        byNorm = names
    }
}

