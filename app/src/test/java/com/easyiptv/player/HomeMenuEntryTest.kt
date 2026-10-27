package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class HomeMenuEntryTest {
    @get:Rule val ui=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun render(startSection: String = "live", startDepth: Int = 1, channelCount: Int = 1, withPreview: Boolean = false) {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("home-entry",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val channels=(0 until channelCount).map { i -> LiveChannel("ch$i",if(i==0) "Test channel" else "Channel $i",null,"news","http://127.0.0.1:9/live$i.ts") }
        val previewQueue=channels.map { Playable(it.name,it.url,true) }
        if(withPreview) Playback.open(ctx,prefs,previewQueue,0,null,false)
        val data=AppData(listOf(Category("news","News and information")),channels,
            emptyList(),listOf(Movie("m","Test movie",null,null,"https://example.invalid/movie.mp4")),
            emptyList(),listOf(SeriesItem("s","Test series",null,null)))
        val source=object : Source by XtreamSource("https://example.invalid","u","p") {
            override suspend fun movieArtwork(movieId:String):String? = null
        }
        ui.setContent {
            val mode=LocalInputModeManager.current
            SideEffect { mode.requestInputMode(InputMode.Keyboard) }
            var section by remember { mutableStateOf(startSection) }
            var depth by remember { mutableIntStateOf(startDepth) }
            var cat by remember { mutableStateOf("all") }
            var query by remember { mutableStateOf("") }
            HomeScreen(prefs=prefs,playlistName="Test",source=source,data=data,loadError=null,
                catalogLoading=false,catalogError=null,onRetryCatalog={},onRefreshMovies={},onRefreshSeries={},
                activeIdx=0,section=section,depth=depth,mini=if(withPreview) MiniState(previewQueue,0,0L) else null,onResumeMini={},onCloseMini={},
                onRoot={section=it;depth=if(it in listOf("live","movies","series")) 1 else 0},
                onBackToRoot={depth=0},liveCat=cat,onLiveCat={cat=it},movieCat="all",onMovieCat={},
                seriesCat="all",onSeriesCat={},searchQuery=query,onSearchQuery={query=it},playlists=emptyList(),
                onSelectPlaylist={},onDeletePlaylist={},onAddPlaylist={},onRetry={},onPlay={},onPlayLive={_,_->},onSeries={})
        }
    }
    private fun focus(text:String) {
        ui.onNodeWithText(text).performSemanticsAction(SemanticsActions.RequestFocus) {it()}
    }
    private fun leftFromChannel() {
        focus("Test channel")
        ui.onNodeWithText("Test channel").performKeyInput {pressKey(Key.DirectionLeft)}
    }
    private fun mainMenu() {
        leftFromChannel()
        category().performKeyInput {pressKey(Key.DirectionLeft)}
    }
    private fun category() = ui.onNode(hasText("All channels") and hasAnyAncestor(hasTestTag("home-rail")))
    @Test fun compactGuideShowsSixFullRowsWithPreviewAndKeepsToolbarAboveGrid() {
        try {
            render(channelCount=12,withPreview=true)
            ui.onNodeWithTag("guide-category-heading").assertIsDisplayed()
            ui.onNodeWithText("Channel 5").assertIsDisplayed()
            val logo=ui.onNodeWithContentDescription("RYZOD Media Player").fetchSemanticsNode().boundsInRoot
            val refresh=ui.onNodeWithText("↻ UPDATE GUIDE").fetchSemanticsNode().boundsInRoot
            val heading=ui.onNodeWithTag("guide-category-heading").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("Brand must be right of centered refresh",logo.left>refresh.right)
            org.junit.Assert.assertTrue("Refresh belongs above the guide",refresh.bottom<heading.top)
            TestScreenshots.save("ryzod479-guide.png")
        } finally {ui.runOnIdle {Playback.releaseAll()}}
    }
    @Test fun guideStartsWithChannelsAndCategoryHeadingOnly() {
        render()
        ui.onNodeWithText("News and information").assertDoesNotExist()
        ui.onNodeWithText("All channels").assertIsDisplayed()
    }
    @Test fun menuButtonKeepsTouchNavigationAvailableWithHiddenRails() {
        render()
        ui.onNodeWithContentDescription("Open menu").performClick()
        ui.onNodeWithText("News and information").assertIsDisplayed()
        ui.onNodeWithText("←  Main menu").assertIsDisplayed()
        ui.onNodeWithText("★ Favorites").assertIsDisplayed()
        ui.onNodeWithContentDescription("Open menu").performClick()
        ui.onNodeWithText("Settings").assertIsDisplayed()
    }
    @Test @Config(qualifiers="w360dp-h800dp-port")
    fun phoneHamburgerShowsMainMenuAndFavoritesImmediately() {
        render()
        ui.onNodeWithContentDescription("Open menu").performClick()
        ui.onNodeWithText("←  Main menu").assertIsDisplayed()
        ui.onNodeWithText("★ Favorites").assertIsDisplayed()
        category().assertIsDisplayed()
    }
    @Test fun leftTwiceOpensCategoriesThenMainMenu() {
        render();leftFromChannel()
        ui.onNodeWithText("News and information").assertIsDisplayed()
        category().assertIsFocused()
        category().performKeyInput {pressKey(Key.DirectionLeft)}
        ui.onNodeWithText("Settings").assertIsDisplayed()
    }
    @Test fun okOnAlreadySelectedCategoryEntersItsChannels() {
        render();leftFromChannel()
        category().performKeyInput {pressKey(Key.DirectionCenter)}
        ui.onNodeWithText("News and information").assertDoesNotExist()
        ui.onNode(hasAnyAncestor(hasTestTag("home-content")) and isFocused()).assertExists()
    }
    @Test fun okOnSearchMovesFocusIntoSearchAndBackReturnsToMenu() {
        render();mainMenu();focus("Search")
        ui.onNodeWithText("Search").performKeyInput {pressKey(Key.DirectionCenter)}
        ui.onNode(hasAnyAncestor(hasTestTag("home-content")) and isFocused()).assertExists()
        ui.onNodeWithText("Settings").assertDoesNotExist()
        ui.runOnIdle {ui.activity.onBackPressedDispatcher.onBackPressed()}
        ui.onNodeWithText("Settings").assertIsDisplayed()
        ui.onNodeWithText("Exit?").assertDoesNotExist()
    }
    @Test fun okOnSettingsMovesFocusToUsableControls() {
        render();mainMenu();focus("Settings")
        ui.onNodeWithText("Settings").performKeyInput {pressKey(Key.DirectionCenter)}
        ui.onNode(hasAnyAncestor(hasTestTag("home-content")) and isFocused()).assertExists()
        ui.onNodeWithText("Search").assertDoesNotExist()
    }
    @Test fun settingsAllowsHorizontalChoicesBeforeReturningLeftToMenu() {
        render();mainMenu();focus("Settings")
        ui.onNodeWithText("Settings").performKeyInput {pressKey(Key.DirectionCenter)}
        ui.onNodeWithText("1 stream").assertIsFocused()
        ui.onNodeWithText("1 stream").performKeyInput {pressKey(Key.DirectionRight)}
        ui.onNodeWithText("2 streams").assertIsFocused()
        ui.onNodeWithText("2 streams").performKeyInput {pressKey(Key.DirectionLeft)}
        ui.onNodeWithText("1 stream").assertIsFocused()
        ui.onNodeWithText("Search").assertDoesNotExist()
        ui.onNodeWithText("1 stream").performKeyInput {pressKey(Key.DirectionLeft)}
        ui.onNodeWithText("Search").assertIsDisplayed()
    }
    @Test fun choosingAProviderCategoryShowsItsNameAboveTheChannels() {
        render();leftFromChannel();focus("News and information")
        ui.onNodeWithText("News and information").performKeyInput {pressKey(Key.DirectionCenter)}
        ui.onNodeWithTag("home-rail").assertDoesNotExist()
        ui.onNodeWithText("News and information").assertIsDisplayed()
        ui.onNodeWithText("Test channel").assertIsFocused()
        ui.runOnIdle { TestScreenshots.save("ryzod478-guide.png") }
    }
    @Test fun movieGridDoesNotStealFocusFromItsCategoryMenu() = checkCatalogEntry("Movies", "All movies")
    @Test fun seriesGridDoesNotStealFocusFromItsCategoryMenu() = checkCatalogEntry("Series", "All series")
    @Test fun openingSettingsDirectlyFromPlaybackEntersItsControls() {
        render("settings",0)
        ui.onNodeWithText("1 stream").assertIsFocused()
        ui.onNodeWithTag("home-rail").assertDoesNotExist()
    }
    @Test fun emptyFavoritesCanReturnLeftToCategories() {
        render();leftFromChannel();focus("★ Favorites")
        ui.onNodeWithText("★ Favorites").performKeyInput {pressKey(Key.DirectionCenter)}
        ui.onNodeWithTag("empty-browse-state").assertIsFocused()
        ui.onNodeWithTag("empty-browse-state").performKeyInput {pressKey(Key.DirectionLeft)}
        ui.onNode(hasText("★ Favorites") and hasAnyAncestor(hasTestTag("home-rail"))).assertIsFocused()
    }
    @Test fun emptyDownloadsCanReturnLeftToMainMenu() = checkEmptySection("Downloads")
    @Test fun emptyRecordingsCanReturnLeftToMainMenu() = checkEmptySection("Recordings")
    private fun checkEmptySection(section:String) {
        render();mainMenu();focus(section)
        ui.onNodeWithText(section).performKeyInput {pressKey(Key.DirectionCenter)}
        ui.onNodeWithTag("empty-browse-state").assertIsFocused()
        ui.onNodeWithTag("empty-browse-state").performKeyInput {pressKey(Key.DirectionLeft)}
        ui.onNode(hasText(section) and hasAnyAncestor(hasTestTag("home-rail"))).assertIsFocused()
    }
    private fun checkCatalogEntry(section:String,category:String) {
        render();mainMenu();focus(section)
        ui.onNodeWithText(section).performKeyInput {pressKey(Key.DirectionCenter)}
        ui.mainClock.advanceTimeBy(500)
        ui.runOnIdle {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        val entry=ui.onNode(hasText(category) and hasAnyAncestor(hasTestTag("home-rail")))
        entry.assertIsFocused()
        entry.performKeyInput {pressKey(Key.DirectionCenter)}
        ui.mainClock.advanceTimeBy(500)
        ui.onNodeWithTag("home-rail").assertDoesNotExist()
        ui.onNode(hasAnyAncestor(hasTestTag("home-content")) and isFocused()).assertExists()
    }
}
