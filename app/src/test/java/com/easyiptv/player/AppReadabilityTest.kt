package com.easyiptv.player

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppReadabilityTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun readabilityMultipliesRatherThanReplacesTheUsersFontScale() {
        // Catches resetting accessibility to 1.30, adding 0.30, or clamping it.
        for ((userScale, effectiveScale) in listOf(0.85f to 1.105f, 1f to 1.30f, 1.2f to 1.56f, 2f to 2.60f)) {
            val base = contextWithFontScale(userScale)
            val wrapped = AppReadability.wrap(base)

            assertEquals(effectiveScale, wrapped.resources.configuration.fontScale, 0.0001f)
            val label = TextView(wrapped).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f) }
            // API 28 uses linear SP conversion: 20sp at xhdpi and the effective scale.
            assertEquals(40f * effectiveScale, label.textSize, 0.01f)
        }
    }

    @Test fun wrappingIsScopedAndKeepsDensityAndAllOtherConfigurationFields() {
        // Catches mutating shared resources or constructing an incomplete Configuration.
        val application = ApplicationProvider.getApplicationContext<Context>()
        val applicationBefore = Configuration(application.resources.configuration)
        val base = contextWithFontScale(1.2f)
        val before = Configuration(base.resources.configuration)
        val densityBefore = base.resources.displayMetrics.density
        val wrapped = AppReadability.wrap(base)

        assertNotSame(base, wrapped)
        assertEquals(before, base.resources.configuration)
        assertEquals(applicationBefore, application.resources.configuration)
        assertEquals(densityBefore, wrapped.resources.displayMetrics.density, 0f)
        assertEquals(200f, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, wrapped.resources.displayMetrics), 0.01f)
        val otherFields = Configuration(wrapped.resources.configuration).apply { fontScale = before.fontScale }
        assertEquals(before, otherFields)

        // Separate activity attachments must each start from the user's unchanged base.
        assertEquals(1.56f, AppReadability.wrap(base).resources.configuration.fontScale, 0.0001f)
    }

    @Test fun aFreshContextUsesTheNewAccessibilitySetting() {
        // Catches caching an effective scale across activity recreation.
        val original = AppReadability.wrap(contextWithFontScale(1f))
        val recreated = AppReadability.wrap(contextWithFontScale(2f))
        assertEquals(1.30f, original.resources.configuration.fontScale, 0.0001f)
        assertEquals(2.60f, recreated.resources.configuration.fontScale, 0.0001f)
    }

    @Test fun activityContextSuppliesTheSameFontConfigurationToComposeAndAnEmbeddedNativeView() {
        // Compose and native views share the font configuration, but their pixel curves can differ.
        // The independent, unwrapped user context catches missing enlargement without assuming linear Compose SP.
        val controlDensity = Density(contextWithFontScale(1.2f))
        val controlTextPx = with(controlDensity) { 20.sp.toPx() }
        val controller = Robolectric.buildActivity(ReadabilityActivity::class.java).setup()
        try {
            val activity = controller.get()
            var composeScale = 0f
            var composeTextPx = 0f
            var composeDpPx = 0f
            var composeContextScale = 0f
            var nativeLabel: TextView? = null
            activity.setContent {
                val density = LocalDensity.current
                val context = LocalContext.current
                SideEffect {
                    composeScale = density.fontScale
                    composeTextPx = with(density) { 20.sp.toPx() }
                    composeDpPx = with(density) { 100.dp.toPx() }
                    composeContextScale = context.resources.configuration.fontScale
                }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(100.dp).testTag("readability-box")) {
                        Text("Readable", fontSize = 20.sp)
                        AndroidView(factory = { context ->
                            TextView(context).apply {
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                                nativeLabel = this
                            }
                        })
                    }
                }
            }
            ui.runOnIdle {
                assertEquals(1.56f, composeScale, 0.0001f)
                assertEquals(1.56f, composeContextScale, 0.0001f)
                assertTrue("Compose text must grow beyond the user's unwrapped 1.2x baseline", composeTextPx > controlTextPx)
                assertEquals(62.4f, requireNotNull(nativeLabel).textSize, 0.01f)
                assertEquals(200f, composeDpPx, 0.01f)
            }
            val bounds = ui.onNodeWithTag("readability-box").fetchSemanticsNode().boundsInRoot
            assertEquals(200f, bounds.width, 0.01f)
            assertEquals(200f, bounds.height, 0.01f)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun playerControlsAndPlatformPickersInheritTheActivityScale() {
        // Catches using unwrapped resources for native controls and dialog themes.
        val controller = Robolectric.buildActivity(ReadabilityActivity::class.java).setup()
        try {
            val activity = controller.get()
            val player = PlayerView(activity)
            val position = requireNotNull(player.findViewById<TextView>(androidx.media3.ui.R.id.exo_position))
            position.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            assertEquals(62.4f, position.textSize, 0.01f)

            val date = DatePickerDialog(activity, null, 2026, 9, 31)
            val time = TimePickerDialog(activity, null, 12, 0, true)
            try {
                date.show()
                time.show()
                for (dialog in listOf(date, time)) {
                    val label = requireNotNull(dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE))
                    assertEquals(1.56f, label.resources.configuration.fontScale, 0.0001f)
                    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    assertEquals(62.4f, label.textSize, 0.01f)
                    assertEquals(2f, label.resources.displayMetrics.density, 0f)
                }
            } finally {
                time.dismiss()
                date.dismiss()
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun contextWithFontScale(scale: Float): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = scale })
    }

    // Deliberately independent of MainActivity: the parent owns its integration.
    class ReadabilityActivity : ComponentActivity() {
        override fun attachBaseContext(newBase: Context) {
            val userContext = newBase.createConfigurationContext(
                Configuration(newBase.resources.configuration).apply { fontScale = 1.2f }
            )
            super.attachBaseContext(AppReadability.wrap(userContext))
        }
    }
}
