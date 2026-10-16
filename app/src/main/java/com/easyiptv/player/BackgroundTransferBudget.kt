package com.easyiptv.player

/** Android shares the dataSync background budget across recording and downloads. */
internal object BackgroundTransferBudget {
    @Volatile var exhausted: Boolean = false
        private set
    fun onTimeout() { exhausted = true }
    // Bringing the app to the foreground resets the platform's dataSync budget.
    fun onForeground() { exhausted = false }
}
