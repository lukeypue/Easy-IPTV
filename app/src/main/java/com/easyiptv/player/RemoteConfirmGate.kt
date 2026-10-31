package com.easyiptv.player

/** A focused control may only activate from a fresh matching OK-down/OK-up pair. */
internal class RemoteConfirmGate {
    private var armedKey: Int? = null
    fun reset() { armedKey = null }
    fun consume(keyCode: Int, action: Int, repeat: Int): Boolean {
        val confirm = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER
        if (!confirm) {
            if (action == android.view.KeyEvent.ACTION_DOWN && when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }) reset()
            return false
        }
        if (action == android.view.KeyEvent.ACTION_DOWN) {
            if (repeat == 0) { armedKey = keyCode; return false }
            return armedKey != keyCode
        }
        if (action == android.view.KeyEvent.ACTION_UP) {
            val owned = armedKey == keyCode
            reset()
            return !owned
        }
        return false
    }
}
