package com.easyiptv.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],qualifiers="w960dp-h540dp-land")
class SearchPlaybackUiTest {
    @get:Rule val ui=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val empty=AppData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())

    @Test fun searchEntryReleasesPlaybackAndDoesNotRestoreTheMiniTvOnExit() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("search-memory",Context.MODE_PRIVATE)
        val queue=listOf(Playable("Preview channel","http://127.0.0.1:9/live.ts",true))
        val section=mutableStateOf("settings")
        val mini=mutableStateOf<MiniState?>(MiniState(queue,0,0))
        try {
            Playback.open(ctx,prefs,queue,0,null,false)
            ui.setContent {
                HomeScreen(prefs=prefs,playlistName="Test",source=null,data=empty,loadError=null,
                    catalogLoading=false,catalogError=null,onRetryCatalog={},onRefreshMovies={},onRefreshSeries={},
                    activeIdx=0,section=section.value,depth=0,mini=mini.value,onResumeMini={},
                    onCloseMini={mini.value=null;Playback.releaseAll()},onRoot={section.value=it},onBackToRoot={},
                    liveCat="all",onLiveCat={},movieCat="all",onMovieCat={},seriesCat="all",onSeriesCat={},
                    searchQuery="",onSearchQuery={},playlists=emptyList(),onSelectPlaylist={},onDeletePlaylist={},
                    onAddPlaylist={},onRetry={},onPlay={},onPlayLive={_,_->},onSeries={})
            }
            ui.runOnIdle {assertNotNull(Playback.player)}
            ui.onNodeWithText("Search",useUnmergedTree=true).performClick()
            ui.runOnIdle {
                assertNull("Search must release the decoder, not merely hide or pause it",Playback.player)
                assertTrue(Playback.queue.isEmpty())
                assertEquals(0,ProviderStreams.playbackSlots())
                assertFalse(Timeshift.active)
                assertNull(mini.value)
            }
            ui.onNodeWithText("Stop playing").assertDoesNotExist()
            ui.onNodeWithText("Settings",useUnmergedTree=true).performClick()
            ui.runOnIdle {assertNull("Leaving Search must not auto-open the old feed",Playback.player)}
        } finally {ui.runOnIdle {Playback.releaseAll()}}
    }

    @Test fun enteringSearchWhileRecordingStillReleasesHiddenPlayback() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("search-record-memory",Context.MODE_PRIVATE)
        val queue=listOf(Playable("Preview channel","http://127.0.0.1:9/live.ts",true))
        val mini=mutableStateOf<MiniState?>(MiniState(queue,0,0))
        try {
            Playback.open(ctx,prefs,queue,0,null,false)
            Recorder.activeName.value="Scheduled recording"
            ui.setContent {
                HomeScreen(prefs=prefs,playlistName="Test",source=null,data=empty,loadError=null,
                    catalogLoading=false,catalogError=null,onRetryCatalog={},onRefreshMovies={},onRefreshSeries={},
                    activeIdx=0,section="search",depth=0,mini=mini.value,onResumeMini={},
                    onCloseMini={mini.value=null;Playback.releaseAll()},onRoot={},onBackToRoot={},
                    liveCat="all",onLiveCat={},movieCat="all",onMovieCat={},seriesCat="all",onSeriesCat={},
                    searchQuery="",onSearchQuery={},playlists=emptyList(),onSelectPlaylist={},onDeletePlaylist={},
                    onAddPlaylist={},onRetry={},onPlay={},onPlayLive={_,_->},onSeries={})
            }
            ui.runOnIdle {
                assertNull("A hidden preview must release even when its view is suppressed by recording",Playback.player)
                assertEquals("Scheduled recording",Recorder.activeName.value)
            }
        } finally {ui.runOnIdle {Recorder.activeName.value=null;Playback.releaseAll()}}
    }
}
