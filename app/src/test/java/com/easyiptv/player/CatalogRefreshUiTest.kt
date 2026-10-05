package com.easyiptv.player

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
class CatalogRefreshUiTest {
    @get:Rule val ui=createComposeRule()
    private val empty=AppData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
    @Test fun moviesCanBeRefreshedEvenWhenCatalogIsEmpty() {
        val prefs=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("easyiptv",Context.MODE_PRIVATE)
        var updates=0
        ui.setContent {MoviesPane(null,prefs,empty,"all",{},onRefresh={updates++})}
        ui.onNodeWithText("UPDATE MOVIES").assertIsDisplayed().assertIsEnabled().performClick()
        ui.runOnIdle {org.junit.Assert.assertEquals(1,updates)}
    }
    @Test fun seriesCanBeRefreshedEvenWhenCatalogIsEmpty() {
        var updates=0
        ui.setContent {SeriesPane(XtreamSource("https://example.invalid","u","p"),empty,"all",{},onRefresh={updates++})}
        ui.onNodeWithText("UPDATE SERIES").assertIsDisplayed().assertIsEnabled().performClick()
        ui.runOnIdle {org.junit.Assert.assertEquals(1,updates)}
    }
}
