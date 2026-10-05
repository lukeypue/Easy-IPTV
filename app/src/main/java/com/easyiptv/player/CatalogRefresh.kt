package com.easyiptv.player

import kotlinx.coroutines.CancellationException

internal object CatalogRefresh {
    enum class Section { ALL, MOVIES, SERIES }
    data class Result(val data:AppData,val warning:String?)
    suspend fun load(source:Source,previous:AppData,section:Section):Result {
        if(!source.supportsSeries) return Result(source.loadAll(),null)
        var next=previous
        val failed=mutableListOf<String>()
        if(section!=Section.SERIES) {
            try {
                val fresh=source.loadMoviesOnly()
                next=next.copy(vodCats=fresh.vodCats,movies=fresh.movies)
            } catch(cancelled:CancellationException) {throw cancelled
            } catch(_:Exception) {failed+="Movies"}
        }
        if(section!=Section.MOVIES) {
            try {
                val fresh=source.loadSeriesOnly()
                next=next.copy(seriesCats=fresh.seriesCats,series=fresh.series)
            } catch(cancelled:CancellationException) {throw cancelled
            } catch(_:Exception) {failed+="Series"}
        }
        return Result(next,failed.takeIf {it.isNotEmpty()}?.joinToString(" and ")?.let {
            "$it could not update. Saved titles are still available; try Update again."
        })
    }
}
