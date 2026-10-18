package com.easyiptv.player

import org.junit.Assert.*
import org.junit.Test

class LiveProgressMonitorTest {
    private fun monitor(): Any {
        val type = runCatching { Class.forName("com.easyiptv.player.LiveProgressMonitor") }.getOrNull()
        assertNotNull("Requested live playback needs recovery even when isPlaying is false", type)
        return type!!.getDeclaredConstructor().newInstance()
    }
    private fun sample(m: Any, now: Long, pos: Long, buffer: Long = 0, eligible: Boolean = true, generation: Long = 1): Boolean =
        m.javaClass.getDeclaredMethod("sample", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            .invoke(m, now, generation, eligible, pos, buffer) as Boolean

    @Test fun stalledLiveClockRecoversAfterGraceEvenWithoutIsPlaying() {
        val m=monitor()
        assertFalse(sample(m,0,60_000)); assertFalse(sample(m,15_000,60_000))
        assertTrue(sample(m,20_000,60_000))
        assertFalse(sample(m,25_000,60_000))
    }
    @Test fun fillingBufferAndAdvancingPlaybackAreHealthy() {
        val m=monitor()
        repeat(40) { assertFalse(sample(m,it*5_000L,it*5_000L)) }
        repeat(10) { assertFalse(sample(m,200_000+it*5_000L,195_000,it*1_000L)) }
    }
    @Test fun intentionalPauseAndBackgroundNeverRecover() {
        val m=monitor()
        sample(m,0,0)
        assertFalse(sample(m,90_000,0,eligible=false))
        assertFalse(sample(m,100_000,0))
        assertFalse(sample(m,115_000,0))
    }
    @Test fun endlessStallsHaveBoundedRetriesAndNewChannelGetsFreshBudget() {
        val m=monitor()
        assertFalse(sample(m,0,0)); assertTrue(sample(m,20_000,0))
        assertFalse(sample(m,40_000,0)); assertTrue(sample(m,50_000,0))
        assertFalse(sample(m,100_000,0)); assertFalse(sample(m,200_000,0))
        assertFalse(sample(m,210_000,0,generation=2)); assertTrue(sample(m,230_000,0,generation=2))
    }
}
