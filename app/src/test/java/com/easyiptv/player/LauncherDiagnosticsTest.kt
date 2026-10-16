package com.easyiptv.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class LauncherDiagnosticsTest {
    @Test @Config(qualifiers="television-tvdpi")
    fun reportIdentifiesInstalledWideArtAndBothLaunchPaths() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val report=LauncherDiagnostics.report(context)
        assertTrue(report,report.contains("RYZOD ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        assertTrue(report,report.contains("uiMode=4"))
        assertTrue(report,report.contains("density=213"))
        assertTrue(report,report.contains(if(BuildConfig.TV_ARTWORK) "app.icon: 1536x1536" else "app.icon: 1672x941"))
        assertTrue(report,report.contains("LEANBACK_LAUNCHER:"))
        assertTrue(report,report.contains("LAUNCHER:"))
        assertTrue(report,report.contains("sha256="))
        assertTrue(report,report.contains("ryzod_launcher_474"))
        assertFalse(report,report.contains("http"))
    }
    @Test @Config(qualifiers="mdpi-notouch")
    fun defaultLoaderConfigurationIdentifiesSquareFallback() {
        val report=LauncherDiagnostics.report(ApplicationProvider.getApplicationContext())
        assertTrue(report,report.contains("app.icon: 1536x1536"))
        assertTrue(report,report.contains("app.banner: 1672x941"))
    }
}
