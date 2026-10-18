package com.easyiptv.player

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.C
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class LiveBufferHealthUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun rawFeedShowsActualBufferRatherThanCallingTheGoalOrQueueALiveOffset() {
        ui.setContent { LiveBufferHealth(2_400,C.TIME_UNSET,60_000,30_000,"Reserve shrinking") }
        ui.onNodeWithText("Buffer ahead 2.4s • Goal 60s • Start 30s • Reserve shrinking").assertIsDisplayed()
        ui.onNodeWithText("Live distance unknown for this feed",substring=true).assertIsDisplayed()
        ui.onNodeWithText("Behind live",substring=true).assertDoesNotExist()
        ui.onNodeWithText("REFILL GOAL").assertIsDisplayed()
    }
    @Test fun knownLiveDistanceAndBufferedAheadRemainSeparateMeasurements() {
        ui.setContent { LiveBufferHealth(12_000,35_000,60_000,10_000,"Reserve refilling") }
        ui.onNodeWithText("Buffer ahead 12.0s",substring=true).assertIsDisplayed()
        ui.onNodeWithText("Behind live 35.0s",substring=true).assertIsDisplayed()
        ui.onNodeWithText("LIVE").assertIsDisplayed()
    }
}
