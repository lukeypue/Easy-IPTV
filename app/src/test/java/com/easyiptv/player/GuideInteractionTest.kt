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
    private fun render(onLeft: () -> Unit = {}, onUp: (() -> Unit)? = null) {
        val now=System.currentTimeMillis()
        val channels=listOf(LiveChannel("a","Family",null,null,"https://example.invalid/a.ts","a"),LiveChannel("b","Suspense",null,null,"https://example.invalid/b.ts","b"))
        val guide=mapOf("a" to listOf(EpgEntry("All Is Lost","",now-60_000,now+7_200_000)),
            "b" to listOf(EpgEntry("This Is My Home","",now-60_000,now+1_200_000),EpgEntry("Heritage Show","",now+1_200_000,now+7_200_000)))
        EpgStore::class.java.getDeclaredField("byChannel").apply { isAccessible=true }.set(EpgStore,guide)
        EpgStore.loaded.value=true
        ui.setContent {
            val inputMode=LocalInputModeManager.current
            SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
            LivePane(prefs,0,AppData(emptyList(),channels,emptyList(),emptyList(),emptyList(),emptyList()),"all",{_,_->}, onLeftToRail=onLeft, onUpToPreview=onUp) }
        ui.waitForIdle()
    }
    @Test fun leftFromChannelReturnsToCategories() {
        var returned=false
        render(onLeft={ returned=true })
        ui.onNodeWithText("Family").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        ui.onNodeWithText("Family").performKeyInput { pressKey(Key.DirectionLeft) }
        ui.runOnIdle { assertTrue("Left from the channel must reach categories",returned) }
    }
    @Test fun upFromFirstProgramReachesPreviewWithoutChangingTime() {
        var reached=false
        render(onUp={ reached=true })
        ui.onNodeWithText("All Is Lost").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        ui.onNodeWithText("All Is Lost").performKeyInput { pressKey(Key.DirectionUp) }
        ui.runOnIdle { assertTrue(reached) }
        ui.onNodeWithText("All Is Lost").assertExists()
    }
    @Test fun allChannelsCanContainRepeatedProviderIds() {
        val channels=(0..30).map { LiveChannel("same","Channel $it",null,null,"https://example.invalid/$it.ts") }
        ui.setContent { LivePane(prefs,0,AppData(emptyList(),channels,emptyList(),emptyList(),emptyList(),emptyList()),"all",{_,_->}) }
        ui.onNodeWithText("Channel 0").assertIsDisplayed()
        ui.onNodeWithText("Channel 0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        ui.onNodeWithText("Channel 0").performKeyInput { pressKey(Key.DirectionDown) }
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
    @Test fun focusedProgramChangesItsWholeBackgroundColor() {
        render()
        ui.onNodeWithText("All Is Lost").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val bounds = ui.onNodeWithText("All Is Lost").fetchSemanticsNode().boundsInWindow
        ui.runOnIdle { TestScreenshots.save("ryzod-focus-filled-guide.png") }
        val image = android.graphics.BitmapFactory.decodeFile("${System.getProperty("java.io.tmpdir")}/ryzod-focus-filled-guide.png")
        // Sample well inside the card, away from its border and text.
        val pixel = image.getPixel((bounds.left + bounds.width * .90f).toInt(), (bounds.top + bounds.height * .80f).toInt())
        val red = android.graphics.Color.red(pixel) / 255f
        val green = android.graphics.Color.green(pixel) / 255f
        val blue = android.graphics.Color.blue(pixel) / 255f
        assertTrue("The whole focused show must have a pink fill, not only an outline: ${Integer.toHexString(pixel)}",
            red > .35f && red > green * 2 && blue > green * 1.5f)
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
            ui.onNodeWithText("Program $i").assertIsDisplayed()
            ui.onNodeWithText("Program ${i+1}").assertIsDisplayed()
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

    @Test fun repeatedDownKeepsTheFocusedProgramVisibleAtLargeTextSize() {
        val now = System.currentTimeMillis()
        val channels = (0..79).map { LiveChannel("row$it", "Channel $it", null, null, "https://example.invalid/$it.ts", "row$it") }
        val guide = channels.mapIndexed { i, ch -> ch.id to listOf(EpgEntry("Program $i", "", now-60_000, now+7_200_000)) }.toMap()
        EpgStore::class.java.getDeclaredField("byChannel").apply { isAccessible=true }.set(EpgStore, guide)
        EpgStore.loaded.value = true
        prefs.edit().putString("last_live_url", channels[30].url).commit()
        ui.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val inputMode = LocalInputModeManager.current
            SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.56f)
            ) {
                LivePane(prefs, 0, AppData(emptyList(), channels, emptyList(), emptyList(), emptyList(), emptyList()), "all", {_,_->})
            }
        }
        ui.onNodeWithText("Channel 30").performKeyInput { pressKey(Key.DirectionRight) }
        ui.onNodeWithText("Program 30").assertIsFocused().assertIsDisplayed()
        var previous = 30
        repeat(15) {
            ui.onRoot().performKeyInput {
                repeat(3) { pressKey(Key.DirectionDown); advanceEventTime(30) }
            }
            ui.waitForIdle()
            val focused = ui.onAllNodes(isFocused() and hasText("Program ", substring = true)).onFirst()
            focused.assertIsDisplayed()
            val row = focused.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .first { it.text.startsWith("Program ") }.text.removePrefix("Program ").toInt()
            assertTrue("Down must advance without jumping or getting stuck: $previous -> $row", row in (previous+1)..(previous+3))
            previous = row
        }
    }
}
