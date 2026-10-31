package com.easyiptv.player

import android.content.Context
import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EnlargedPlayerUiTest {
    @get:Rule val ui=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun televisionPlayerControlsFitAtTheLargerAppScale() = checkPlayer("tv")
    @Test @Config(qualifiers="w360dp-h800dp-port")
    fun phonePlayerControlsRemainReadableWithoutSplittingTheirCaptions() = checkPlayer("phone")
    private fun checkPlayer(name:String) {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("large-player-$name",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            ui.setContent {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,1.30f)) {
                    MaterialTheme(colorScheme=darkColorScheme()) {
                        PlayerScreen((0..3).map {Playable("Channel $it","http://127.0.0.1:9/$it.ts",true)},0,source=null,prefs=prefs,onOpenSettings={_,_->},onBack={_,_->})
                    }
                }
            }
            ui.runOnIdle {PlayerKeys.priority?.invoke(KeyEvent.KEYCODE_DPAD_CENTER)}
            for(word in listOf("❚❚ PAUSE","PREVIOUS","SIZE","ⓘ INFO")) {
                val node=ui.onNodeWithText(word,useUnmergedTree=true).assertIsDisplayed()
                val layouts=mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
                assertTrue("$word layout exists",layouts.isNotEmpty())
                for(layout in layouts) {
                    assertEquals("$word should remain one readable caption",1,layout.lineCount)
                    assertFalse("$word must not lose characters",layout.multiParagraph.didExceedMaxLines)
                    assertTrue("$word glyphs fit vertically",layout.getLineBottom(0)<=layout.size.height+1f)
                }
            }
            ui.runOnIdle {TestScreenshots.save("ryzod484-player-$name.png")}
        } finally {ui.runOnIdle {Playback.releaseAll()}}
    }
}
