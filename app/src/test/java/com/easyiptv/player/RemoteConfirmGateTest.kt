package com.easyiptv.player
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
class RemoteConfirmGateTest {
    @Test fun orphanReleasesAndRepeatsCannotActivateANewFocusTarget() {
        val gate=RemoteConfirmGate()
        assertTrue(gate.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0))
        assertTrue(gate.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,2))
        assertTrue(gate.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0))
    }
    @Test fun navigationOrFocusLossCancelsAnOwnedPress() {
        val gate=RemoteConfirmGate()
        assertFalse(gate.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0))
        assertFalse(gate.consume(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.ACTION_DOWN,0))
        assertTrue(gate.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0))
        assertFalse(gate.consume(KeyEvent.KEYCODE_ENTER,KeyEvent.ACTION_DOWN,0))
        gate.reset()
        assertTrue(gate.consume(KeyEvent.KEYCODE_ENTER,KeyEvent.ACTION_UP,0))
    }
    @Test fun freshMatchingPressAndReleaseRemainsAvailableToTheControl() {
        val gate=RemoteConfirmGate()
        assertFalse(gate.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0))
        assertFalse(gate.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0))
    }
}
