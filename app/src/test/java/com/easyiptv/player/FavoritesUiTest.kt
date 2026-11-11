package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class FavoritesUiTest {
    @get:Rule val ui = createComposeRule()
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("favorites-ui", Context.MODE_PRIVATE)
    private val playlist = Playlist("Provider A", "xtream", "https://example.invalid", "a")
    private val channel = LiveChannel("1", "Evening News", null, null, "https://example.invalid/live.ts")
    private val movie = Movie("1", "Favorite Movie", null, null, "https://example.invalid/movie.mp4")
    private val series = SeriesItem("1", "Favorite Series", null, null)
    private val source = object : Source by XtreamSource(playlist.host, playlist.user, "test") {
        override suspend fun mediaInfo(movieId: String): MediaInfo? = null
        override suspend fun seriesEpisodes(seriesId: String) = mapOf(
            1 to listOf(Episode("1", "Pilot", 1, 1, "https://example.invalid/episode.mp4"))
        )
    }
    private val catalogVisible = mutableStateOf(true)
    private var played: Playable? = null

    @Before fun reset() { prefs.edit().clear().commit() }

    private fun render(initial: String) {
        val store = FavoritesStore(prefs, playlist, 0)
        ui.setContent {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            var section by remember { mutableStateOf(initial) }
            var depth by remember { mutableIntStateOf(1) }
            var liveCat by remember { mutableStateOf("all") }
            var openSeries by remember { mutableStateOf<SeriesItem?>(null) }
            val currentSeries = openSeries
            if (currentSeries != null) {
                SeriesDetailScreen(source, currentSeries, prefs, { queue, index -> played = queue[index] },
                    onBack = { openSeries = null }, favorites = store)
            } else {
                val data = if (catalogVisible.value) AppData(emptyList(), listOf(channel), emptyList(), listOf(movie),
                    emptyList(), listOf(series)) else AppData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                HomeScreen(prefs = prefs, playlistName = playlist.name, source = source, data = data, loadError = null,
                    catalogLoading = false, catalogError = null, onRetryCatalog = {}, onRefreshMovies = {}, onRefreshSeries = {},
                    activeIdx = 0, section = section, depth = depth, mini = null, onResumeMini = {}, onCloseMini = {},
                    onRoot = { section = it; depth = if (it in setOf("live", "movies", "series")) 1 else 0 },
                    onBackToRoot = { depth = 0 }, liveCat = liveCat, onLiveCat = { liveCat = it }, movieCat = "all",
                    onMovieCat = {}, seriesCat = "all", onSeriesCat = {}, searchQuery = "", onSearchQuery = {},
                    playlists = listOf(playlist), onSelectPlaylist = {}, onDeletePlaylist = {}, onAddPlaylist = {},
                    onRetry = {}, onPlay = { played = it }, onPlayLive = { queue, index -> played = queue[index] },
                    onSeries = { openSeries = it }, favoriteStore = store)
            }
        }
    }

    private fun openFavorites() {
        repeat(2) { ui.onNodeWithContentDescription("Open menu").performClick() }
        val tab = ui.onNode(hasText("Favorites") and hasAnyAncestor(hasTestTag("home-rail")))
        tab.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        tab.performKeyInput { pressKey(Key.DirectionCenter) }
    }

    @Test fun movieFavoritesKeepPlaybackAndDownloadWithoutLoadedCatalog() {
        render("movies")
        ui.onNodeWithText(movie.name).performClick()
        val play = ui.onNodeWithText("▶ PLAY").fetchSemanticsNode().boundsInRoot.center
        val favorite = ui.onNodeWithText("☆ ADD FAVORITE").fetchSemanticsNode().boundsInRoot.center
        assertTrue("Favorite follows Play on the same row", play.x < favorite.x && kotlin.math.abs(play.y - favorite.y) < 1f)
        ui.onNodeWithText("☆ ADD FAVORITE").performClick()
        ui.onNodeWithText("CLOSE").performClick()
        ui.runOnIdle { catalogVisible.value = false }
        openFavorites()
        ui.onNodeWithText(movie.name).performClick()
        ui.onNodeWithText("⬇ DOWNLOAD").assertIsDisplayed()
        ui.onNodeWithText("▶ PLAY").performClick()
        ui.runOnIdle { assertEquals(movie.url, played?.url) }
        ui.onNodeWithText(movie.name).performClick()
        ui.onNodeWithText("★ REMOVE FAVORITE").performClick()
        ui.onNodeWithText(movie.name).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun movieFavoriteActionsStayInlineOnANarrowScreen() {
        render("movies")
        ui.onNodeWithText("ⓘ").performClick()
        ui.onNodeWithText("▶ PLAY").assertExists()
        fun assertRow(favoriteLabel: String) {
            val labels = listOf("▶ PLAY", favoriteLabel, "⬇ DOWNLOAD", "CLOSE")
            val positions = labels.map {
                val node = ui.onNodeWithText(it)
                assertTrue("$it must be visible on a narrow screen", node.isDisplayed())
                node.fetchSemanticsNode().boundsInRoot.center
            }
            assertTrue(positions.zipWithNext().all { (a, b) -> a.x < b.x && kotlin.math.abs(a.y - b.y) < 1f })
        }
        assertRow("☆ ADD FAVORITE")
        ui.onNodeWithText("☆ ADD FAVORITE").performClick()
        assertRow("★ REMOVE FAVORITE")

    }

    @Test fun seriesFavoritesOpenSeasonsAndCanBeRemoved() {
        render("series")
        ui.onNodeWithText(series.name).performClick()
        ui.onNodeWithText("Season 1").performClick()
        ui.onNodeWithText("E1  Pilot").performClick()
        val play = ui.onNodeWithText("▶ PLAY").fetchSemanticsNode().boundsInRoot.center
        val favorite = ui.onNodeWithText("☆ ADD FAVORITE").fetchSemanticsNode().boundsInRoot.center
        assertTrue("Favorite follows episode Play on the same row", play.x < favorite.x && kotlin.math.abs(play.y - favorite.y) < 1f)
        ui.onNodeWithText("☆ ADD FAVORITE").performClick()
        ui.onNodeWithText("CLOSE").performClick()
        repeat(2) { ui.onNodeWithContentDescription("Back").performClick() }
        openFavorites()
        ui.onNodeWithText(series.name).performClick()
        ui.onNodeWithText("OPEN SEASONS").performClick()
        ui.onNodeWithText("Season 1").assertIsDisplayed().performClick()
        ui.onNodeWithText("E1  Pilot").assertIsDisplayed()
        repeat(2) { ui.onNodeWithContentDescription("Back").performClick() }
        ui.onNodeWithText(series.name).performClick()
        ui.onNodeWithText("★ REMOVE FAVORITE").performClick()
        ui.onNodeWithText(series.name).assertDoesNotExist()
    }

    @Test fun guidePopupPlacesFavoriteBetweenWatchAndRecordOnTheSameRow() {
        render("live")
        ui.onAllNodesWithText("No information").onFirst().performClick()
        val watch = ui.onNodeWithText("▶ WATCH").assertIsDisplayed()
        val favorite = ui.onNodeWithText("FAVORITE").assertIsDisplayed()
        val record = ui.onNodeWithText("● RECORD").assertIsDisplayed()
        val close = ui.onNodeWithText("CLOSE").assertIsDisplayed()
        val actions = listOf(watch, favorite, record, close).map { it.fetchSemanticsNode().boundsInRoot.center }
        assertTrue(actions.zipWithNext().all { (a, b) -> a.x < b.x && kotlin.math.abs(a.y - b.y) < 1f })
        watch.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        watch.performKeyInput { pressKey(Key.DirectionRight) }
        favorite.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        ui.onNodeWithText("UNFAVORITE").assertIsDisplayed().performClick()
        favorite.assertIsDisplayed()
    }

    @Test fun channelTimerCanFavoriteAndSavedChannelOffersWatchAndRecord() {
        render("live")
        ui.onNodeWithText(channel.name).performClick()
        ui.onNodeWithText("☆ ADD FAVORITE").performClick()
        ui.onNodeWithText("CLOSE").performClick()
        openFavorites()
        ui.onNodeWithText(channel.name).performClick()
        ui.onNodeWithText("● SCHEDULE RECORDING").assertIsDisplayed()
        ui.onNodeWithText("▶ WATCH").performClick()
        ui.runOnIdle { assertEquals(channel.url, played?.url); assertTrue(played?.isLive == true) }
        ui.onNodeWithText(channel.name).performClick()
        ui.onNodeWithText("★ REMOVE FAVORITE").performClick()
        ui.onNodeWithText(channel.name).assertDoesNotExist()
    }

    @Test fun oldChannelFavoritesStayWithTheProviderWhenItsSlotChanges() {
        val other = playlist.copy(name = "Provider B", user = "b")
        prefs.edit().putStringSet("fav_live_1", setOf(channel.id)).commit()
        FavoritesStore.migrateLive(prefs, listOf(playlist, other))
        assertTrue(FavoritesStore(prefs, other, 0).contains(FavoriteItem.from(channel)))
        assertFalse(FavoritesStore(prefs, playlist, 1).contains(FavoriteItem.from(channel)))
    }

    @Test fun savedM3uMovieSurvivesReorderingAndIsSeparateFromSeries() {
        val m3u = Playlist("List", "m3u", url = "https://example.invalid/list")
        val original = movie.copy(id = "m3u_1")
        FavoritesStore(prefs, m3u, 0).toggle(FavoriteItem.from(original))
        val restored = FavoritesStore(prefs, m3u, 0)
        assertTrue(restored.contains(FavoriteItem.from(original.copy(id = "m3u_5"))))
        assertFalse(restored.contains(FavoriteItem.from(original.copy(url = "https://example.invalid/other.mp4"))))
        assertFalse(restored.contains(FavoriteItem.from(series.copy(id = original.id))))
    }
}
