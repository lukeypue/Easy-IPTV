package com.easyiptv.player

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class)
class LibraryItemMenusTest {
    @get:Rule val ui = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
    private var played: Playable? = null

    @Before fun reset() {
        prefs.edit().clear().commit()
        Recorder.recordingsDir(context).listFiles()?.forEach { it.delete() }
        played = null
    }

    private fun download(state: Int): DownloadStore.Item {
        val file = File(context.cacheDir, "menu-test.mp4")
        file.delete()
        File(file.path + ".part").delete()
        if (state == DownloadStore.STATE_SUCCESS) file.writeBytes(byteArrayOf(1, 2, 3))
        else File(file.path + ".part").writeBytes(byteArrayOf(4, 5, 6))
        val item = DownloadStore.Item(465, "Test movie", file.path, Long.MAX_VALUE)
        DownloadStore.save(prefs, listOf(item))
        DownloadStore.mark(context, item.id, state, 3, 30)
        return item
    }

    @Test fun completedDownloadOpensMenuBeforePlaying() {
        download(DownloadStore.STATE_SUCCESS)
        ui.setContent { DownloadsPane(prefs) { played = it } }
        ui.onNodeWithText("Test movie").performClick()
        ui.runOnIdle { assertNull("Selecting a download must not start playback", played) }
        ui.onNodeWithText("PLAY").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals("Test movie", played?.name) }
    }

    @Test fun unfinishedDownloadOpensMenuAndOffersResume() {
        download(DownloadStore.STATE_FAILED)
        ui.setContent { DownloadsPane(prefs) { played = it } }
        ui.onNodeWithText("Test movie").performClick()
        ui.onNodeWithText("CLOSE").assertExists()
        ui.onNodeWithText("PLAY").assertIsNotEnabled()
        ui.onNodeWithText("RESUME").assertIsEnabled()
        ui.onNodeWithText("DELETE").assertIsEnabled()
    }

    @Test fun remoteOpensMenuAndWrapsFromPlayToClose() {
        download(DownloadStore.STATE_SUCCESS)
        ui.setContent { DownloadsPane(prefs) { played = it } }
        ui.onNodeWithText("Test movie").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        ui.onNodeWithText("Test movie").performKeyInput { pressKey(Key.DirectionCenter) }
        ui.onNodeWithText("PLAY").assertIsFocused()
        ui.onNodeWithText("PLAY").performKeyInput { pressKey(Key.DirectionLeft) }
        ui.onNodeWithText("CLOSE").assertIsFocused()
        ui.onNodeWithText("CLOSE").performKeyInput { pressKey(Key.DirectionCenter) }
        ui.onNodeWithText("Test movie").assertExists()
        ui.onNodeWithText("CLOSE").assertDoesNotExist()
        ui.runOnIdle { assertNull(played) }
    }

    @Test fun pauseDownloadPreservesPartialFileAndItem() {
        val item = download(DownloadStore.STATE_RUNNING)
        ui.setContent { DownloadsPane(prefs) { played = it } }
        ui.onNodeWithText("Test movie").performClick()
        ui.onNodeWithText("PAUSE DOWNLOAD").assertIsEnabled().performClick()
        ui.runOnIdle {
            assertTrue(File(item.path + ".part").exists())
            assertEquals(1, DownloadStore.load(prefs).size)
            assertFalse(DownloadStore.isInFlight(context, item.id))
        }
    }

    @Test fun openDownloadMenuUpdatesWhenTransferCompletes() {
        val item = download(DownloadStore.STATE_RUNNING)
        ui.setContent { DownloadsPane(prefs) { played = it } }
        ui.onNodeWithText("Test movie").performClick()
        ui.onNodeWithText("PLAY").assertIsNotEnabled()
        ui.runOnIdle {
            File(item.path).writeBytes(byteArrayOf(1, 2, 3))
            DownloadStore.mark(context, item.id, DownloadStore.STATE_SUCCESS, 3, 3)
        }
        ui.mainClock.advanceTimeBy(2_000)
        ui.onNodeWithText("PLAY").assertIsEnabled()
        ui.onNodeWithText("PAUSE DOWNLOAD").assertDoesNotExist()
    }

    @Test fun openDownloadMenuOffersResumeAfterConnectionFailure() {
        val item = download(DownloadStore.STATE_RUNNING)
        ui.setContent { DownloadsPane(prefs) { played = it } }
        ui.onNodeWithText("Test movie").performClick()
        ui.onNodeWithText("RESUME").assertIsNotEnabled()
        ui.runOnIdle { DownloadStore.mark(context, item.id, DownloadStore.STATE_FAILED, 3, 30, "Connection lost") }
        ui.mainClock.advanceTimeBy(2_000)
        ui.onNodeWithText("RESUME").assertIsEnabled()
        ui.onNodeWithText("PAUSE DOWNLOAD").assertIsNotEnabled()
    }

    @Test fun downloadDeleteRequiresConfirmationAndCancelKeepsFile() {
        val item = download(DownloadStore.STATE_SUCCESS)
        ui.setContent { DownloadsPane(prefs) { played = it } }
        ui.onNodeWithText("Test movie").performClick()
        ui.onNodeWithText("DELETE").performClick()
        ui.runOnIdle { assertTrue(File(item.path).exists()) }
        ui.onNodeWithText("CANCEL").performClick()
        ui.runOnIdle { assertTrue(File(item.path).exists()) }
        ui.onNodeWithText("Test movie").performClick()
        ui.onNodeWithText("DELETE").performClick()
        ui.onNodeWithText("YES, DELETE").performClick()
        ui.runOnIdle {
            assertFalse(File(item.path).exists())
            assertTrue(DownloadStore.load(prefs).isEmpty())
        }
    }

    private fun recording(): File = File(Recorder.recordingsDir(context), "REC_Test_show.ts")
        .apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun recordingOpensMenuBeforePlaying() {
        recording()
        ui.setContent { RecordingsPane(prefs) { played = it } }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Test show").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Test show").performClick()
        ui.runOnIdle { assertNull("Selecting a recording must not start playback", played) }
        ui.onNodeWithText("PLAY").assertIsEnabled().performClick()
        ui.runOnIdle { assertNotNull(played) }
    }

    @Test fun recordingDeleteRequiresConfirmationAndCancelKeepsFile() {
        val file = recording()
        ui.setContent { RecordingsPane(prefs) { played = it } }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Test show").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Test show").performClick()
        ui.onNodeWithText("DELETE").performClick()
        ui.runOnIdle { assertTrue(file.exists()) }
        ui.onNodeWithText("CANCEL").performClick()
        ui.runOnIdle { assertTrue(file.exists()) }
        ui.onNodeWithText("Test show").performClick()
        ui.onNodeWithText("DELETE").performClick()
        ui.onNodeWithText("YES, DELETE").performClick()
        ui.waitUntil(10_000) { !file.exists() }
    }
}
