package com.easyiptv.player

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class DistributionTest {
    @Test fun storeBuildsCannotRequestApkInstallation() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val permissions=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertEquals(BuildConfig.SELF_UPDATES,permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertEquals(36,context.applicationInfo.targetSdkVersion)
        assertEquals("com.easyiptv.player",context.packageName)
    }
}
