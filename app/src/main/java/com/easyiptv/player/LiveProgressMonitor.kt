package com.easyiptv.player

/** Checks requested foreground live playback, including BUFFERING with isPlaying=false.
 * Position or forward-buffer growth earns time; an intentional pause is ineligible. */
internal class LiveProgressMonitor {
    private var session=Long.MIN_VALUE
    private var position=-1L
    private var buffer=-1L
    private var progressAt=-1L
    private var lastRecovery=Long.MIN_VALUE
    private var recoveries=0
    private var healthySince=-1L

    fun sample(nowMs: Long, generation: Long, eligible: Boolean, positionMs: Long, bufferMs: Long): Boolean {
        if(session!=generation) {
            session=generation; recoveries=0; lastRecovery=Long.MIN_VALUE
            progressAt=-1L; position=-1L; buffer=-1L; healthySince=-1L
        }
        if(!eligible) { progressAt=-1L; position=-1L; buffer=-1L; healthySince=-1L; return false }
        val advancing=positionMs>position && position>=0
        if(progressAt<0 || advancing || bufferMs>buffer+200 || positionMs<position) {
            progressAt=nowMs
            if(advancing) {
                if(healthySince<0) healthySince=nowMs
                if(nowMs-healthySince>=60_000) recoveries=0
            } else healthySince=-1L
        } else healthySince=-1L
        position=positionMs; buffer=bufferMs
        if(nowMs-progressAt<20_000 || recoveries>=2 ||
            (lastRecovery!=Long.MIN_VALUE && nowMs-lastRecovery<30_000)) return false
        recoveries++; lastRecovery=nowMs; progressAt=nowMs
        return true
    }
}
