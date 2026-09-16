package com.easyiptv.player

// ZAKO_V447_STARTUP_INPUT_GATE
enum class StartupGateState { NO_PLAYLIST, LOADING, READY, ERROR }

/** Keeps half-refreshed playlist state from receiving remote input. */
internal object StartupPolicy {
    const val loadingMessage = "Please wait while we load your content fresh for a better experience"

    fun state(hasPlaylist: Boolean, refreshSettled: Boolean, failed: Boolean = false): StartupGateState = when {
        !hasPlaylist -> StartupGateState.NO_PLAYLIST
        failed -> StartupGateState.ERROR
        !refreshSettled -> StartupGateState.LOADING
        else -> StartupGateState.READY
    }

    fun shouldBlockInput(hasPlaylist: Boolean, refreshSettled: Boolean): Boolean =
        state(hasPlaylist, refreshSettled) == StartupGateState.LOADING
}

/** Activity-level gate so Fire TV remote presses cannot reach stale/partial UI. */
internal object AppInputGate {
    @Volatile var startupLocked: Boolean = false
}
