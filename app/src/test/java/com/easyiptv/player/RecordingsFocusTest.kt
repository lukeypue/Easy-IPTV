package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class RecordingsFocusTest {
    @get:Rule val ui=createComposeRule()
    @Test fun delayedFileScanMovesFocusFromTheEmptyStateToARecording() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("recordings-delayed-focus",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val loaded=CompletableDeferred<List<File>>()
        val recording=File(ctx.cacheDir,"REC_Test_recording.ts").apply {writeBytes(ByteArray(1024))}
        ui.setContent {
            val mode=LocalInputModeManager.current
            SideEffect {mode.requestInputMode(InputMode.Keyboard)}
            RecordingsPane(prefs,{},requestInitialFocus=true,loadFiles={loaded.await()})
        }
        ui.onNodeWithTag("empty-browse-state").assertIsFocused()
        ui.runOnIdle {loaded.complete(listOf(recording))}
        ui.onNodeWithText("Test recording").assertIsFocused()
        ui.onNodeWithTag("empty-browse-state").assertDoesNotExist()
    }
}
