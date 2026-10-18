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
class StreamBufferPolicyTest {
    @Test fun everySettingCombinationLeavesRoomToRefillAndKeepsSteadyDistinct() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("buffer-combinations",Context.MODE_PRIVATE)
        for(size in listOf(10,30,60)) for(lock in listOf(3_000,4_000,6_000,10_000)) {
            fun policy(steady:Boolean):StreamBufferPolicy {
                prefs.edit().clear().putInt("buffer_sec",size).putInt("live_start_ms",lock)
                    .putBoolean("live_steady_recovery",steady).commit()
                return StreamBufferPolicy.from(prefs)
            }
            val natural=policy(false)
            val steady=policy(true)
            assertTrue("Steady must rebuild more than Natural: size=$size lock=$lock",steady.rebufferMs>natural.rebufferMs)
            for(p in listOf(natural,steady)) {
                assertTrue(p.reserveMs<=60_000)
                assertTrue(p.reserveMs>p.rebufferMs)
                assertTrue(p.reserveMs>p.startMs)
                assertTrue(p.rebufferMs>=p.startMs)
            }
        }
    }
    @Test fun oldOrCorruptNumericPreferencesCannotCreateNegativeOrUnboundedBuffers() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("buffer-invalid",Context.MODE_PRIVATE)
        for(value in listOf(Int.MIN_VALUE,-1,0,Int.MAX_VALUE)) {
            prefs.edit().clear().putInt("buffer_sec",value).putInt("live_start_ms",value).commit()
            val p=StreamBufferPolicy.from(prefs)
            assertTrue(p.startMs in 2_000..12_000)
            assertTrue(p.rebufferMs in p.startMs..30_000)
            assertTrue(p.reserveMs in p.rebufferMs..60_000)
        }
    }
}
