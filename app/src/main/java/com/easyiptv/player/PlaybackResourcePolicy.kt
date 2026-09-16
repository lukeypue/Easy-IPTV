package com.easyiptv.player

// ZAKO_V447_PLAYBACK_PRIORITY_POLICY
object PlaybackResourcePolicy {
    /** Heavy catalog/index work yields while live playback is settling or buffering. */
    fun allowBackgroundHeavyWork(livePlaying: Boolean, playerBuffering: Boolean): Boolean =
        !livePlaying || !playerBuffering

    /** Keep page-ahead bounded on low-memory TV hardware. */
    fun catalogPrefetchPages(livePlaying: Boolean): Int = if (livePlaying) 1 else 2

    /** Downloads may continue during stable playback, but never multiply provider sockets. */
    fun maxConcurrentDownloads(): Int = 1
}
