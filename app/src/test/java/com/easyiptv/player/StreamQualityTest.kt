package com.easyiptv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StreamQualityTest {
    // Reflection makes absence an assertion failure in the parent's pre-implementation RED run.
    // All manifest metadata and parameter mutations below use actual Media3 objects.
    private class Controller {
        private val instance: Any
        init {
            val type = runCatching { Class.forName("com.easyiptv.player.StreamQualityController") }.getOrNull()
            assertNotNull("Missing optional StreamQualityController", type)
            instance = type!!.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        }
        private fun invoke(name: String, types: Array<Class<*>>, vararg args: Any?): Any? =
            instance.javaClass.getDeclaredMethod(name, *types).apply { isAccessible = true }
                .invoke(instance, *args)
        val isLimited: Boolean get() = invoke("isLimited", emptyArray()) as Boolean
        fun canLimit(tracks: Tracks) = invoke("canLimit", arrayOf(Tracks::class.java), tracks) as Boolean
        fun setLimited(player: Player, limited: Boolean) = invoke("setLimited",
            arrayOf(Player::class.java, Boolean::class.javaPrimitiveType!!), player, limited) as Boolean
        fun reset(player: Player?) { invoke("reset", arrayOf(Player::class.java), player) }
    }

    private fun video(width: Int, height: Int) = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(width).setHeight(height).build()

    private fun group(
        vararg formats: Format,
        selected: Set<Int> = setOf(formats.lastIndex),
        support: IntArray = IntArray(formats.size) { C.FORMAT_HANDLED },
        id: String = "main",
    ) = Tracks.Group(TrackGroup(id, *formats), true, support,
        BooleanArray(formats.size) { it in selected })

    private fun tracks(vararg groups: Tracks.Group) = Tracks(groups.toList())
    private fun adaptive() = group(video(640, 360), video(1280, 720), video(1920, 1080))

    // Forward only the manifest boundary; every parameter read/write reaches real ExoPlayer.
    private class ManifestPlayer(val actual: ExoPlayer, var manifest: Tracks) : ForwardingPlayer(actual) {
        override fun getCurrentTracks(): Tracks = manifest
    }

    private fun withPlayer(manifest: Tracks = tracks(adaptive()), block: (ManifestPlayer) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actual = ExoPlayer.Builder(context).build()
        try { block(ManifestPlayer(actual, manifest)) } finally { actual.release() }
    }

    @Test fun selectedAdaptiveGroupOffersLimitWithOnlyHighVariantSelected() {
        assertTrue(Controller().canLimit(tracks(adaptive())))
    }

    @Test fun selectedMultipleVariantsAndCurrentlyLowVideoRemainEligible() {
        val controller = Controller()
        assertTrue(controller.canLimit(tracks(group(video(1280, 720), video(1920, 1080), selected = setOf(0, 1)))))
        assertTrue(controller.canLimit(tracks(group(video(640, 360), video(1920, 1080), selected = setOf(0)))))
    }

    @Test fun emptyAudioOnlyAndSingleFixedFeedsCannotBeLimited() {
        val controller = Controller()
        assertFalse(controller.canLimit(Tracks.EMPTY))
        assertFalse(controller.canLimit(tracks(group(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()))))
        assertFalse(controller.canLimit(tracks(group(video(1920, 1080)))))
        assertFalse(controller.canLimit(tracks(group(video(1280, 720)))))
    }

    @Test fun lowOnlyAndHighOnlyAlternativesCannotOfferA720Limit() {
        val controller = Controller()
        assertFalse(controller.canLimit(tracks(group(video(640, 360), video(1280, 720)))))
        assertFalse(controller.canLimit(tracks(group(video(1920, 1080), video(3840, 2160)))))
    }

    @Test fun unknownZeroOrPartiallyKnownDimensionsDoNotCountAsLowOrHigh() {
        val controller = Controller()
        for ((width, height) in listOf(-1 to -1, -1 to 720, 1280 to -1, 0 to 720, 1280 to 0)) {
            assertFalse("Invalid low $width x $height", controller.canLimit(tracks(group(video(width, height), video(1920, 1080)))))
            assertFalse("Invalid high $width x $height", controller.canLimit(tracks(group(video(640, 360), video(width, height)))))
        }
    }

    @Test fun bothWidthAndHeightMustFitThe720Rectangle() {
        val controller = Controller()
        assertFalse(controller.canLimit(tracks(group(video(1920, 720), video(1920, 1080)))))
        assertFalse(controller.canLimit(tracks(group(video(720, 1280), video(1080, 1920)))))
        assertTrue(controller.canLimit(tracks(group(video(1280, 720), video(1281, 720)))))
        assertTrue(controller.canLimit(tracks(group(video(1280, 720), video(1280, 721)))))
    }

    @Test fun unsupportedOrExceedsCapabilitiesVariantsDoNotEstablishEitherAlternative() {
        val controller = Controller()
        for (unsupported in listOf(C.FORMAT_UNSUPPORTED_TYPE, C.FORMAT_UNSUPPORTED_SUBTYPE,
            C.FORMAT_EXCEEDS_CAPABILITIES)) {
            assertFalse(controller.canLimit(tracks(group(video(1280, 720), video(1920, 1080),
                support = intArrayOf(unsupported, C.FORMAT_HANDLED)))))
            assertFalse(controller.canLimit(tracks(group(video(1280, 720), video(1920, 1080),
                selected = setOf(0), support = intArrayOf(C.FORMAT_HANDLED, unsupported)))))
        }
    }

    @Test fun unselectedCameraCannotSupplyAlternativesEvenWhenItIsAdaptive() {
        val controller = Controller()
        val otherCamera = group(video(1280, 720), video(1920, 1080), selected = emptySet(), id = "other-camera")
        assertFalse(controller.canLimit(tracks(otherCamera)))
        assertFalse(controller.canLimit(tracks(group(video(1920, 1080)), otherCamera)))
        assertFalse(controller.canLimit(tracks(group(video(1920, 1080)),
            group(video(640, 360), selected = emptySet(), id = "low-camera"))))
        assertTrue(controller.canLimit(tracks(otherCamera, adaptive())))
    }

    @Test fun enableMutatesRealPlayerBoundsAndPreservesAudioCaptionsAndOtherPreferences() {
        val controller = Controller()
        withPlayer { player ->
            player.actual.trackSelectionParameters = player.actual.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage("fr").setPreferredTextLanguage("es")
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).setMaxVideoBitrate(8_000_000).build()
            val before = player.actual.trackSelectionParameters
            assertFalse(controller.isLimited)
            assertTrue(controller.setLimited(player, true))
            assertTrue(controller.isLimited)
            assertEquals(before.buildUpon().setMaxVideoSize(1280, 720).build(), player.actual.trackSelectionParameters)
            assertFalse(player.actual.trackSelectionParameters.forceLowestBitrate)
        }
    }

    @Test fun rejectedEnableDoesNotChangeRealParametersOrArmTheController() {
        val controller = Controller()
        withPlayer(tracks(group(video(1920, 1080)))) { player ->
            val before = player.actual.trackSelectionParameters
            assertFalse(controller.setLimited(player, true))
            assertEquals(before, player.actual.trackSelectionParameters)
            assertFalse(controller.isLimited)
        }
    }

    @Test fun enableNeverRaisesAnExistingStricterWidthOrHeight() {
        for ((width, height, cappedWidth, cappedHeight) in listOf(
            listOf(854, 480, 854, 480), listOf(960, 1080, 960, 720), listOf(1920, 540, 1280, 540))) {
            val controller = Controller()
            withPlayer { player ->
                player.actual.trackSelectionParameters = player.actual.trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(width, height).build()
                assertTrue(controller.setLimited(player, true))
                assertEquals(cappedWidth, player.actual.trackSelectionParameters.maxVideoWidth)
                assertEquals(cappedHeight, player.actual.trackSelectionParameters.maxVideoHeight)
                controller.reset(player)
                assertEquals(width, player.actual.trackSelectionParameters.maxVideoWidth)
                assertEquals(height, player.actual.trackSelectionParameters.maxVideoHeight)
            }
        }
    }

    @Test fun disableRestoresOnlyOriginalVideoOverridesAndBoundsKeepingLaterAudioCaptionEdits() {
        val controller = Controller()
        val video = adaptive()
        val videoOverride = TrackSelectionOverride(video.mediaTrackGroup, listOf(2))
        val textGroup = TrackGroup("captions", Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build())
        val audioGroup = TrackGroup("audio", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build())
        val textOverride = TrackSelectionOverride(textGroup, listOf(0))
        val audioOverride = TrackSelectionOverride(audioGroup, listOf(0))
        withPlayer(tracks(video)) { player ->
            player.actual.trackSelectionParameters = player.actual.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(2560, 1440).addOverride(videoOverride).addOverride(textOverride).build()
            assertTrue(controller.setLimited(player, true))
            assertFalse(player.actual.trackSelectionParameters.overrides.containsKey(video.mediaTrackGroup))
            assertEquals(textOverride, player.actual.trackSelectionParameters.overrides[textGroup])
            player.actual.trackSelectionParameters = player.actual.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT).addOverride(audioOverride)
                .setPreferredAudioLanguage("de").setPreferredTextLanguage("it")
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            val whileLimited = player.actual.trackSelectionParameters
            player.manifest = Tracks.EMPTY // The channel may disappear before the viewer turns the limit off.
            assertTrue(controller.setLimited(player, false))
            assertFalse(controller.isLimited)
            assertEquals(whileLimited.buildUpon().setMaxVideoSize(2560, 1440)
                .addOverride(videoOverride).build(), player.actual.trackSelectionParameters)
            assertFalse(player.actual.trackSelectionParameters.overrides.containsKey(textGroup))
        }
    }

    @Test fun repeatedEnableDoesNotReplaceTheOriginalRestorationSnapshot() {
        val controller = Controller()
        withPlayer { player ->
            val before = player.actual.trackSelectionParameters
            assertTrue(controller.setLimited(player, true))
            controller.setLimited(player, true)
            player.manifest = Tracks.EMPTY
            controller.setLimited(player, true)
            assertTrue(controller.isLimited)
            controller.setLimited(player, false)
            assertEquals(before, player.actual.trackSelectionParameters)
        }
    }

    @Test fun resetRestoresVideoStatePreservesLaterPreferencesAndAllowsFreshChannelSnapshot() {
        val controller = Controller()
        val video = adaptive()
        val override = TrackSelectionOverride(video.mediaTrackGroup, listOf(2))
        withPlayer(tracks(video)) { player ->
            player.actual.trackSelectionParameters = player.actual.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(1920, 1080).addOverride(override).build()
            assertTrue(controller.setLimited(player, true))
            player.actual.trackSelectionParameters = player.actual.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage("ja").setPreferredTextLanguage("en").build()
            val edited = player.actual.trackSelectionParameters
            player.manifest = Tracks.EMPTY
            controller.reset(player)
            assertFalse(controller.isLimited)
            assertEquals(edited.buildUpon().setMaxVideoSize(1920, 1080).addOverride(override).build(),
                player.actual.trackSelectionParameters)
            player.actual.trackSelectionParameters = player.actual.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(3840, 2160).clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
            val nextChannel = player.actual.trackSelectionParameters
            player.manifest = tracks(adaptive())
            assertTrue(controller.setLimited(player, true))
            controller.reset(player)
            assertEquals(nextChannel, player.actual.trackSelectionParameters)
            controller.reset(player)
            assertEquals(nextChannel, player.actual.trackSelectionParameters)
        }
    }

    @Test fun nullResetClearsStateAndDoesNotRestoreReleasedPlayersSettingsOntoANewPlayer() {
        val controller = Controller()
        withPlayer { old ->
            old.actual.trackSelectionParameters = old.actual.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(1920, 1080).build()
            assertTrue(controller.setLimited(old, true))
            controller.reset(null)
            assertFalse(controller.isLimited)
        }
        withPlayer { fresh ->
            val before = fresh.actual.trackSelectionParameters
            controller.reset(fresh)
            assertEquals(before, fresh.actual.trackSelectionParameters)
            assertTrue(controller.setLimited(fresh, true))
            controller.reset(fresh)
            assertEquals(before, fresh.actual.trackSelectionParameters)
        }
    }
}
