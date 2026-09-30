package com.easyiptv.player

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LauncherArtworkTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun assertWideArtwork(drawable: Drawable?) {
        assertTrue("TV tile must be a legacy bitmap drawable", drawable is BitmapDrawable)
        val artwork = drawable as BitmapDrawable
        val expected = context.getDrawable(R.drawable.ryzod_tv_artwork_466) as BitmapDrawable
        assertTrue("The wide artwork must fill the tile", artwork.bitmap.sameAs(expected.bitmap))
        assertEquals(16.0 / 9.0, artwork.bitmap.width.toDouble() / artwork.bitmap.height, 0.002)
        assertEquals(android.view.Gravity.FILL, artwork.gravity)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun televisionUsesWideArtworkForApplicationActivityAndIcon() {
        val pm = context.packageManager
        val app = pm.getApplicationInfo(context.packageName, 0)
        val activity = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertWideArtwork(app.loadBanner(pm))
        assertWideArtwork(activity.loadBanner(pm))
        assertWideArtwork(app.loadIcon(pm))
        assertWideArtwork(activity.loadIcon(pm))
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-notnight-xhdpi")
    fun defaultConfigurationHasWideLegacyFallback() {
        val pm = context.packageManager
        val icon = pm.getApplicationInfo(context.packageName, 0).loadIcon(pm)
        assertWideArtwork(icon)
        val original = context.getDrawable(R.drawable.ryzod_artwork_464) as BitmapDrawable
        assertEquals(original.bitmap.width, original.bitmap.height)
    }
}
