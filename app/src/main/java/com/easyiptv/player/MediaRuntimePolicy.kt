package com.easyiptv.player

// ZAKO_V447_MEDIA_RUNTIME
object MediaRuntimePolicy {
    const val maxConcurrentDownloads = 1

    const val oneStreamRecordingMessage =
        "Your provider is configured as a 1-stream IPTV plan. Recording another channel may pause live TV."

    const val storageFallbackMessage =
        "USB storage is unavailable or not writable. Zako will safely use internal storage instead."

    fun canStartBackgroundDownload(livePlaying: Boolean, playerBuffering: Boolean): Boolean =
        PlaybackResourcePolicy.allowBackgroundHeavyWork(livePlaying, playerBuffering)

    fun recordingWarning(maxProviderStreams: Int, recordingCurrentLiveChannel: Boolean): String? =
        if (maxProviderStreams <= 1 && !recordingCurrentLiveChannel) oneStreamRecordingMessage else null

    fun shouldUseUsb(usbAvailable: Boolean, usbWritable: Boolean): Boolean =
        usbAvailable && usbWritable
}
