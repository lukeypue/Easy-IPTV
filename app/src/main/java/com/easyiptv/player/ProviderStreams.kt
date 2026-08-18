package com.easyiptv.player

import android.content.Context
import android.content.SharedPreferences

/**
 * Small connection-budget helper. IPTV subscriptions often allow 1, 2 or 3
 * simultaneous streams. EZTV defaults to 1 and never guesses higher.
 *
 * Same-channel DVR recording is special: it copies from the existing timeshift
 * file and therefore consumes ZERO additional provider connections.
 */
object ProviderStreams {
    private const val KEY = "provider_streams"

    fun max(prefs: SharedPreferences): Int = prefs.getInt(KEY, 1).coerceIn(1, 3)

    fun setMax(prefs: SharedPreferences, value: Int) {
        prefs.edit().putInt(KEY, value.coerceIn(1, 3)).apply()
    }

    /** Remote playback uses one provider slot. Local downloaded/recorded files use none. */
    fun playbackSlots(): Int = Playback.providerConnectionSlots()

    /** A tee recording uses the live DVR file and costs no extra provider slot. */
    fun recordingSlots(): Int = if (Recorder.usesProviderConnection) 1 else 0

    /** DownloadManager owns one remote HTTP stream while a provider download is active. */
    fun downloadSlots(context: Context, prefs: SharedPreferences): Int =
        if (DownloadStore.hasInFlight(context, prefs)) 1 else 0

    fun used(context: Context, prefs: SharedPreferences): Int =
        playbackSlots() + recordingSlots() + downloadSlots(context, prefs)

    fun canUse(context: Context, prefs: SharedPreferences, additional: Int): Boolean =
        used(context, prefs) + additional <= max(prefs)

    fun label(prefs: SharedPreferences): String = when (max(prefs)) {
        1 -> "1 provider stream"
        else -> "${max(prefs)} provider streams"
    }
}
