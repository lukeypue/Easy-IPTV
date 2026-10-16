package com.easyiptv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** A loading/home screen cannot revive the last channel just by starting an Activity. */
@Composable
internal fun SyncPlaybackVisibility(videoReady: Boolean) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, videoReady) {
        fun sync() {
            if(videoReady && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                Playback.resumeFromBackground()
            } else {
                Playback.pauseForVisibility()
            }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        owner.lifecycle.addObserver(observer)
        sync()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
