package com.easyiptv.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VodPictureRecoveryTest {
    @Test fun restartsTheCurrentItemAtItsExistingPosition() {
        val playback = TestPlayback()
        assertTrue(restartOnDemandPictureAtCurrentPosition(playback.player))
        assertEquals(listOf("stop", "seekTo:67000", "prepare"), playback.actions)
    }

    @Test fun leavesPausedAndUnseekableTitlesAlone() {
        for (playback in listOf(TestPlayback(playing = false), TestPlayback(seekable = false),
            TestPlayback(canSeek = false))) {
            assertFalse(restartOnDemandPictureAtCurrentPosition(playback.player))
            assertTrue(playback.actions.isEmpty())
        }
    }

    private class TestPlayback(playing: Boolean = true, seekable: Boolean = true, canSeek: Boolean = true) {
        val actions = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "isPlaying" -> playing
                "isCurrentMediaItemSeekable" -> seekable
                "isCommandAvailable" -> canSeek && args.single() == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
                "getCurrentPosition" -> 67_000L
                "stop", "prepare", "seekTo" -> {
                    actions += if (method.name == "seekTo") "seekTo:${args.single()}" else method.name
                    null
                }
                else -> error("Unexpected player call ${method.name}")
            }
        } as Player
    }
}
