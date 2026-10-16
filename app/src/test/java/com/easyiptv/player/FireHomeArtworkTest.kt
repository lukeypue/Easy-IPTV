package com.easyiptv.player

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FireHomeArtworkTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun checkArtwork(television: Boolean) {
        val pm = context.packageManager
        val app = pm.getApplicationInfo(context.packageName, 0)
        val activity = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val square = context.getDrawable(R.drawable.ryzod_artwork_464) as BitmapDrawable
        val wide = context.getDrawable(R.drawable.ryzod_tv_artwork_466) as BitmapDrawable
        val expected = if (television && !BuildConfig.TV_ARTWORK) wide else square
        for (id in listOf(app.icon, activity.icon, activity.logo, R.mipmap.ryzod_launcher_474)) {
            val drawable = context.getDrawable(id)
            assertTrue("Launcher must expose a bitmap", drawable is BitmapDrawable)
            assertTrue("Launcher must preserve the approved artwork and its proportions", (drawable as BitmapDrawable).bitmap.sameAs(expected.bitmap))
        }
        for (banner in listOf(app.loadBanner(pm), activity.loadBanner(pm))) {
            assertTrue(banner is BitmapDrawable)
            assertTrue("TV banner must retain the approved wide artwork", (banner as BitmapDrawable).bitmap.sameAs(wide.bitmap))
        }
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun televisionSeparatesSquareFireIconFromWideBanner() = checkArtwork(true)

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-notnight-xhdpi")
    fun fallbackConfigurationPreservesSquareIcon() = checkArtwork(false)
}
