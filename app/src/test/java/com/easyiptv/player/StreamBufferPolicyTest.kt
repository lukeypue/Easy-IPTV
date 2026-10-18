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
        for(size in listOf(10,30,60)) for(lock in listOf(0,3_000,4_000,6_000,10_000,20_000,30_000)) {
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
    @Test fun thirtySecondCushionIsNotSilentlyClampedToTwelveSeconds() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("long-cushion",Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("live_start_ms",30_000).commit()
        val p=StreamBufferPolicy.from(prefs)
        assertEquals(30_000,p.startMs)
        assertTrue(p.rebufferMs>=30_000)
        assertTrue(p.reserveMs>p.rebufferMs)
    }
    @Test fun zeroReserveMeansNoAddedStartupOrRecoveryWait() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("no-cushion",Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("buffer_sec",0).putInt("live_start_ms",30_000)
            .putBoolean("live_steady_recovery",true).commit()
        val p=StreamBufferPolicy.from(prefs)
        assertEquals(0,p.startMs)
        assertEquals(0,p.rebufferMs)
    }
    @Test fun zeroCushionDoesNotManufactureASteadyStartupWait() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("zero-lock",Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("live_start_ms",0).putBoolean("live_steady_recovery",true).commit()
        assertEquals(0,StreamBufferPolicy.from(prefs).startMs)
    }
    @Test fun oldOrCorruptNumericPreferencesCannotCreateNegativeOrUnboundedBuffers() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("buffer-invalid",Context.MODE_PRIVATE)
        for(value in listOf(Int.MIN_VALUE,-1,0,Int.MAX_VALUE)) {
            prefs.edit().clear().putInt("buffer_sec",value).putInt("live_start_ms",value).commit()
            val p=StreamBufferPolicy.from(prefs)
            assertTrue(p.startMs in 0..30_000)
            assertTrue(p.rebufferMs in p.startMs..50_000)
            assertTrue(p.reserveMs in p.rebufferMs..60_000)
        }
    }
}
