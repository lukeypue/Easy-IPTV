package com.easyiptv.player

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class UpdateDistributionTest {
    private val root="https://github.com/lukeypue/Easy-IPTV/releases/download/v4.75/"
    @Test fun tvSelectsWidePackageAndPhoneSelectsUniversal() {
        val manifest=JSONObject().put("downloadUrl",root+"phone.apk").put("downloadUrlTv",root+"fire.apk")
        assertEquals(root+"fire.apk",UpdateDistribution.downloadUrl(manifest,true))
        assertEquals(root+"phone.apk",UpdateDistribution.downloadUrl(manifest,false))
    }
    @Test fun olderManifestsRemainSupportedOnTv() {
        assertEquals(root+"phone.apk",UpdateDistribution.downloadUrl(JSONObject().put("downloadUrl",root+"phone.apk"),true))
    }
    @Test(expected=IllegalStateException::class)
    fun rejectsUnexpectedTvDownloadHost() {
        UpdateDistribution.downloadUrl(JSONObject().put("downloadUrl",root+"phone.apk").put("downloadUrlTv","https://example.invalid/file.apk"),true)
    }
}
