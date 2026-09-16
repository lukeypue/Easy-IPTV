package com.easyiptv.player

// ZAKO_V447_TV_SHELL
object TvShell {
    enum class Destination { LIVE, GUIDE, MOVIES, SERIES, SEARCH, LIBRARY, SETTINGS }
    enum class FocusToken { MAIN_NAV, CATEGORY_RAIL, CONTENT, OVERLAY }

    val destinations = Destination.entries

    fun initialDestination(): Destination = Destination.LIVE
    fun initialFocus(): FocusToken = FocusToken.MAIN_NAV

    fun leftFrom(token: FocusToken): FocusToken = when (token) {
        FocusToken.CONTENT -> FocusToken.CATEGORY_RAIL
        FocusToken.CATEGORY_RAIL -> FocusToken.MAIN_NAV
        FocusToken.OVERLAY -> FocusToken.CONTENT
        FocusToken.MAIN_NAV -> FocusToken.MAIN_NAV
    }

    fun rightFrom(token: FocusToken): FocusToken = when (token) {
        FocusToken.MAIN_NAV -> FocusToken.CATEGORY_RAIL
        FocusToken.CATEGORY_RAIL -> FocusToken.CONTENT
        FocusToken.CONTENT -> FocusToken.CONTENT
        FocusToken.OVERLAY -> FocusToken.OVERLAY
    }
}
