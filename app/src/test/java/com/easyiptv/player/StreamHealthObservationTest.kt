package com.easyiptv.player

import org.junit.Assert.*
import org.junit.Test

class StreamHealthObservationTest {
    private fun input(drops: Int = 0, paused: Boolean = false) = StreamHealthInput(
        !paused, true, true, 0, StreamTransferSnapshot(0L, 0, null, 1, 5_000),
        6_000_000L, drops, 30, false, false)
    private fun assess(value: StreamHealthInput, known: Boolean): Any {
        val method = Class.forName("com.easyiptv.player.StreamHealthKt").declaredMethods.firstOrNull {
            it.name == "assessStreamHealth" && it.parameterCount == 2
        }
        assertNotNull("Missing prefetch-aware delivery assessment", method)
        return method!!.apply { isAccessible = true }.invoke(null, value, known)!!
    }
    private fun title(value: Any) = value.javaClass.getDeclaredField("title").apply { isAccessible = true }.get(value).toString()
    @Test fun prefetchWithoutCommittedBytesDoesNotInventAStalledDelivery() {
        assertEquals("Collecting complete segment", title(assess(input(), false)))
    }
    @Test fun measuredDeliveryStarvationStillShowsAnInterruption() {
        assertEquals("Delivery interruption detected", title(assess(input(), true)))
    }
    @Test fun pendingDeliveryDoesNotHideFrameDrops() {
        assertEquals("Frame drops detected", title(assess(input(drops = 6), false)))
    }
    @Test fun pendingDeliveryDoesNotChangeIntentionalPause() {
        assertEquals("Paused", title(assess(input(paused = true), false)))
    }
}
