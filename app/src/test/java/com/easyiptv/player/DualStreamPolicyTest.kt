package com.easyiptv.player

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DualStreamPolicyTest {
    private fun plan(enabled: Boolean?, maximum: Int, background: Int = 0,
        url: String = "https://example.test/live.m3u8?token=PRIVATE_TOKEN",
        copies: Int = 2, smooth: Boolean = false): Any {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("dual-policy", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("provider_streams", maximum).apply {
            if (enabled != null) putBoolean("dual_stream_trial", enabled)
            putInt("dual_stream_count", copies)
            putBoolean("live_smooth", smooth)
        }.commit()
        val type = runCatching { Class.forName("com.easyiptv.player.DualStreamPolicyKt") }.getOrNull()
        assertNotNull("Missing dual trial format/budget gate", type)
        return type!!.getDeclaredMethod("planDualStreamTrial", SharedPreferences::class.java, String::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(null, prefs, url, background)!!
    }
    private fun value(plan: Any, name: String) = plan.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(plan)
    private fun check(result: Any, active: Boolean, slots: Int) {
        assertEquals(active, value(result, "active"))
        assertEquals(slots, value(result, "slots"))
        assertFalse(value(result, "status").toString().contains("PRIVATE_TOKEN"))
        assertFalse(value(result, "status").toString().contains("example.test"))
    }
    @Test fun missingPreferenceNeverOpensASecondConnection() = check(plan(null, 3), false, 1)
    @Test fun offAlwaysKeepsOneConnection() = check(plan(false, 3), false, 1)
    @Test fun oneStreamAccountCannotActivateTheTrial() = check(plan(true, 1), false, 1)
    @Test fun twoAvailableSlotsAllowHlsTrial() = check(plan(true, 2), true, 2)
    @Test fun threeStreamAccountCanRecordAlongsideTrial() = check(plan(true, 3, 1), true, 2)
    @Test fun backgroundWorkLeavesSingleStreamFallbackWhenOnlyOneSlotIsFree() = check(plan(true, 3, 2), false, 1)
    @Test fun rawTsDoesNotOpenASecondConnection() = check(plan(true, 3, url = "https://example.test/live.ts"), false, 1)
    @Test fun localPlaylistCannotOpenProviderConnections() = check(plan(true, 3, url = "file:///tmp/live.m3u8"), false, 1)
    @Test fun threeCopyTrialReservesThreeFreeSlots() = check(plan(true, 3, copies = 3), true, 3)
    @Test fun threeCopyTrialFallsBackToTwoWhileRecording() = check(plan(true, 3, 1, copies = 3), true, 2)
    @Test fun smoothKeepsTheTrialOutOfTheImmediatePath() = check(plan(true, 3, copies = 3, smooth = true), false, 1)
    @Test fun unknownBudgetFailsClosed() = check(plan(true, 3, -1), false, 1)
}
