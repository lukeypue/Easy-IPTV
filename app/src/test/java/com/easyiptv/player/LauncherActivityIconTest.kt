package com.easyiptv.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LauncherActivityIconTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Config(sdk = [26, 28, 30], qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun televisionLaunchActivityExposesLegacyBitmapIconAndLogo() {
        val pm = context.packageManager
        val activity = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertTrue("Fire TV launchers must have an explicit activity icon", activity.icon != 0)
        assertTrue("Fire TV launchers must have an explicit activity logo", activity.logo != 0)
        for (icon in listOf(activity.loadIcon(pm), activity.loadLogo(pm), activity.loadBanner(pm))) {
            assertTrue("TV artwork must resolve directly to a legacy bitmap", icon is BitmapDrawable)
            val bitmap = (icon as BitmapDrawable).bitmap
            assertEquals(16.0 / 9.0, bitmap.width.toDouble() / bitmap.height, 0.002)
            val expected = context.getDrawable(R.drawable.ryzod_tv_artwork_466) as BitmapDrawable
            assertTrue("Use the approved wide logo", bitmap.sameAs(expected.bitmap))
        }
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setPackage(context.packageName)
        val resolved = pm.resolveActivity(launch, 0)
        assertNotNull("The TV launch intent must resolve", resolved)
        assertEquals(MainActivity::class.java.name, resolved!!.activityInfo.name)
        assertTrue("The resolved launch icon must also be a bitmap", resolved.loadIcon(pm) is BitmapDrawable)
    }

    @Test
    @Config(sdk = [28], qualifiers = "w360dp-h640dp-port-notnight-xhdpi")
    fun defaultLauncherConfigurationAlsoExposesWideArtwork() {
        val pm = context.packageManager
        val activity = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertTrue(activity.loadIcon(pm) is BitmapDrawable)
        assertTrue(activity.applicationInfo.loadIcon(pm) is BitmapDrawable)
    }
}
