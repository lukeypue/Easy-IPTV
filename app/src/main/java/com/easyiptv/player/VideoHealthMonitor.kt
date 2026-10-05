package com.easyiptv.player

/** Coarse renderer-progress checks. No frame callbacks, timers, or player ownership. */
internal class VideoHealthMonitor {
    private var session = Long.MIN_VALUE
    private var lastPosition = -1L
    private var lastFrames = -1L
    private var frameProgressAt = 0L
    private var lastRecoveryAt = Long.MIN_VALUE
    private var recoveries = 0
    private var healthySince = -1L
    var stableProgressMs = 0L
        private set

    private var audioSession = Long.MIN_VALUE
    private var lastAudioPosition = -1L
    private var audioHealthySince = -1L

    /** Only the controller's foreground, playing, READY audio-only path is eligible. */
    fun sampleAudioProgress(nowMs: Long, generation: Long, eligible: Boolean,
                            positionMs: Long): Boolean {
        if (audioSession != generation || !eligible || positionMs <= lastAudioPosition) {
            audioSession = generation
            lastAudioPosition = if (eligible) positionMs else -1L
            audioHealthySince = -1L
            return false
        }
        if (lastAudioPosition < 0L) {
            lastAudioPosition = positionMs
            return false
        }
        lastAudioPosition = positionMs
        if (audioHealthySince < 0L) audioHealthySince = nowMs
        return nowMs - audioHealthySince >= 15_000L
    }

    /** Eligibility is supplied by Playback: visible video, READY, playing, foreground. */
    fun sample(nowMs: Long, generation: Long, eligible: Boolean,
               positionMs: Long, renderedFrames: Long): Boolean {
        if (session != generation) {
            session = generation
            recoveries = 0
            lastRecoveryAt = Long.MIN_VALUE
            clearObservation()
        }
        if (!eligible || renderedFrames <= 0L) {
            clearObservation()
            return false
        }
        if (lastFrames < 0L || renderedFrames < lastFrames || positionMs < lastPosition) {
            lastFrames = renderedFrames
            lastPosition = positionMs
            frameProgressAt = nowMs
            healthySince = -1L
            stableProgressMs = 0L
            return false
        }
        val positionAdvanced = positionMs > lastPosition
        val framesAdvanced = renderedFrames > lastFrames
        lastPosition = positionMs
        lastFrames = renderedFrames
        if (framesAdvanced) {
            frameProgressAt = nowMs
            if (positionAdvanced) {
                if (healthySince < 0L) healthySince = nowMs
                stableProgressMs = nowMs - healthySince
                // A sustained healthy picture earns another bounded recovery cycle.
                if (stableProgressMs >= 60_000L) recoveries = 0
            } else {
                healthySince = -1L
                stableProgressMs = 0L
            }
            return false
        }
        healthySince = -1L
        stableProgressMs = 0L
        if (!positionAdvanced) {
            frameProgressAt = nowMs
            return false
        }
        val cooldownPassed = lastRecoveryAt == Long.MIN_VALUE || nowMs - lastRecoveryAt >= 30_000L
        if (nowMs - frameProgressAt < 15_000L || !cooldownPassed || recoveries >= 2) return false
        recoveries++
        lastRecoveryAt = nowMs
        frameProgressAt = nowMs
        return true
    }

    private fun clearObservation() {
        lastPosition = -1L
        lastFrames = -1L
        healthySince = -1L
        stableProgressMs = 0L
    }
}
