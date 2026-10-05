package com.easyiptv.player

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class GuideInteractionTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val prefs get()=context.getSharedPreferences("easyiptv",Context.MODE_PRIVATE)
    @Before fun setup() { prefs.edit().clear().commit(); EpgStore.clear() }
    @After fun clean() { EpgStore.clear() }
    private fun render() {
        val now=System.currentTimeMillis()
        val channels=listOf(LiveChannel("a","Family",null,null,"https://example.invalid/a.ts","a"),LiveChannel("b","Suspense",null,null,"https://example.invalid/b.ts","b"))
        val guide=mapOf("a" to listOf(EpgEntry("All Is Lost","",now-60_000,now+7_200_000)),
            "b" to listOf(EpgEntry("This Is My Home","",now-60_000,now+1_200_000),EpgEntry("Heritage Show","",now+1_200_000,now+7_200_000)))
        EpgStore::class.java.getDeclaredField("byChannel").apply { isAccessible=true }.set(EpgStore,guide)
        EpgStore.loaded.value=true
        ui.setContent {
            val inputMode=LocalInputModeManager.current
            SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
            LivePane(prefs,0,AppData(emptyList(),channels,emptyList(),emptyList(),emptyList(),emptyList()),"all",{_,_->}) }
        ui.waitForIdle()
    }
    @Test fun downFromWideProgramSelectsTheProgramBesideTheNextChannel() {
        render()
        ui.onNodeWithText("All Is Lost").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        ui.onNodeWithText("All Is Lost").assertIsFocused()
        ui.onNodeWithText("All Is Lost").performKeyInput { pressKey(Key.DirectionDown) }
        ui.onNodeWithText("This Is My Home").assertIsFocused()
        ui.onNodeWithText("This Is My Home").performKeyInput { pressKey(Key.DirectionRight) }
        ui.onNodeWithText("Heritage Show").assertIsFocused()
    }
    @Test fun downAcrossUncomposedRowsKeepsFirstProgramFocus() {
        val now=System.currentTimeMillis()
        val channels=(0..24).map { LiveChannel("row$it","Channel $it",null,null,"https://example.invalid/$it.ts","row$it") }
        val guide=channels.mapIndexed { i,ch -> ch.id to listOf(EpgEntry("Program $i","",now-60_000,now+7_200_000)) }.toMap()
        EpgStore::class.java.getDeclaredField("byChannel").apply {isAccessible=true}.set(EpgStore,guide)
        EpgStore.loaded.value=true
        ui.setContent {
            val inputMode=LocalInputModeManager.current
            SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
            LivePane(prefs,0,AppData(emptyList(),channels,emptyList(),emptyList(),emptyList(),emptyList()),"all",{_,_->}) }
        ui.onNodeWithText("Program 0").performSemanticsAction(SemanticsActions.RequestFocus) {it()}
        ui.onNodeWithText("Program 0").assertIsFocused()
        repeat(24) { i ->
            ui.onNodeWithText("Program $i").performKeyInput {pressKey(Key.DirectionDown)}
            ui.waitUntil(5_000) {ui.onAllNodesWithText("Program ${i+1}").filter(isFocused()).fetchSemanticsNodes().isNotEmpty()}
        }
        ui.onNodeWithText("Program 24").performKeyInput {pressKey(Key.DirectionDown)}
        ui.onNodeWithText("Program 24").assertIsFocused()
        repeat(24) { i ->
            ui.onNodeWithText("Program ${24-i}").performKeyInput {pressKey(Key.DirectionUp)}
            ui.onNodeWithText("Program ${23-i}").assertIsFocused()
        }
        ui.onNodeWithText("Program 0").performKeyInput {pressKey(Key.DirectionUp)}
        ui.onNodeWithText("Program 0").assertIsFocused()
    }
    @Test fun manualRecordingHasHalfHourChoicesAndSavesSelectedStop() {
        render(); ui.onNodeWithText("Family").performClick()
        ui.onNodeWithText("5 hr").assertIsDisplayed()
        ui.runOnIdle { TestScreenshots.save("ryzod-471-manual.png") }
        ui.onNodeWithText("1.5 hr").performClick()
        // Tomorrow gives a future start regardless of test execution hour.
        val tomorrow=java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR,1) }
        ui.onNodeWithText(java.text.SimpleDateFormat("EEE M/d",java.util.Locale.getDefault()).format(tomorrow.time)).performClick()
        ui.onNodeWithText("RECORD").performClick(); ui.onNodeWithText("YES, RECORD").performClick()
        ui.runOnIdle { val s=ScheduleStore.load(prefs).single(); assertEquals(5_400_000L,s.endMs-s.startMs) }
    }
}
