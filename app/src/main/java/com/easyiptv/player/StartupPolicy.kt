package com.easyiptv.player

/** Keeps half-refreshed playlist state from receiving remote input. */
internal object StartupPolicy {
    fun shouldBlockInput(hasPlaylist: Boolean, refreshSettled: Boolean): Boolean =
        hasPlaylist && !refreshSettled
}

/** Activity-level gate so Fire TV remote presses cannot reach stale/partial UI. */
internal object AppInputGate {
    @Volatile var startupLocked: Boolean = false
}
