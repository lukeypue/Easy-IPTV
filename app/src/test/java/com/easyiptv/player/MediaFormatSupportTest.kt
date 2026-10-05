package com.easyiptv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MediaFormatSupportTest {
    @Test fun packagedFactoriesAcceptCommonLiveTransportFormats() {
        val factory=DefaultMediaSourceFactory(ApplicationProvider.getApplicationContext<Context>())
        for((uri,type) in listOf("https://example.invalid/live.m3u8" to C.CONTENT_TYPE_HLS,
            "https://example.invalid/live.mpd" to C.CONTENT_TYPE_DASH,
            "https://example.invalid/live.ism/Manifest" to C.CONTENT_TYPE_SS,
            "rtsp://example.invalid/live" to C.CONTENT_TYPE_RTSP,
            "https://example.invalid/live.ts" to C.CONTENT_TYPE_OTHER)) {
            assertTrue("No packaged media source for $uri",type in factory.supportedTypes)
            assertNotNull(factory.createMediaSource(MediaItem.fromUri(uri)))
        }
    }
}
