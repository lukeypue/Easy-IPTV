package com.easyiptv.player

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class LiveControlsRegressionTest {
    @Test fun consumedRemotePressOwnsItsRepeatAndRelease() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        var calls=0
        try {
            AppInputGate.startupLocked=false
            PlayerKeys.priority={key -> if(key==KeyEvent.KEYCODE_DPAD_CENTER) {calls++;true} else false}
            val a=controller.get()
            assertTrue(a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)))
            assertTrue(a.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,1)))
            assertTrue("Release must not activate the newly focused Pause button",a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER)))
            assertEquals("Holding OK must open the guide only once",1,calls)
        } finally {PlayerKeys.priority=null;controller.pause().stop().destroy()}
    }
    @Test fun readyDoesNotEraseAnExhaustedPauseRecoveryBudget() {
        val field=Playback.javaClass.getDeclaredField("stallRestarts").apply {isAccessible=true}
        try {
            field.setInt(Playback,2)
            Playback.noteReadyForStall()
            assertEquals(2,field.getInt(Playback))
        } finally {Playback.releaseAll()}
    }
    @Test fun intentionalPauseNeverTriggersTheBufferingWatchdog() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("pause-watchdog",Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("live_steady_recovery",true).commit()
        try {
            val p=Playback.open(context,prefs,listOf(Playable("Live","http://127.0.0.1:9/live.ts",true)),0,null,false)
            Playback.pauseForConfiguration()
            Playback.javaClass.getDeclaredField("simpleRaw").apply {isAccessible=true}.setBoolean(Playback,false)
            Playback.playStateC.intValue=androidx.media3.common.Player.STATE_BUFFERING
            val now=System.currentTimeMillis()
            fun field(name:String)=Playback.javaClass.getDeclaredField(name).apply {isAccessible=true}
            field("bufferingSince").setLong(Playback,now-60_000)
            field("lastBufGrowthAt").setLong(Playback,now-60_000)
            field("lastBufMs").setLong(Playback,p.totalBufferedDuration)
            val generation=Timeshift.generation()
            (field("governorTick").get(Playback) as Runnable).run()
            assertFalse("A user pause must never be automatically resumed",p.playWhenReady)
            assertEquals(generation,Timeshift.generation())
        } finally {Playback.releaseAll()}
    }
    @Test fun automaticDirectRetryPreservesAnIntentionalPause() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("paused-retry",Context.MODE_PRIVATE)
        try {
            val p=Playback.open(context,prefs,listOf(Playable("Live","http://127.0.0.1:9/live.ts",true)),0,null,false)
            Playback.pauseForConfiguration()
            assertFalse(p.playWhenReady)
            Playback.zapTo(0,preserveDirect=true)
            assertFalse("Automatic retry must preserve the paused request",p.playWhenReady)
            assertFalse(Timeshift.active)
        } finally {Playback.releaseAll()}
    }
    @Test fun foregroundReturnPreservesThePriorPlaybackRequest() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("paused-foreground",Context.MODE_PRIVATE)
        for(playing in listOf(false,true)) {
            try {
                val p=Playback.open(context,prefs,listOf(Playable("Live","http://127.0.0.1:9/live.ts",true)),0,null,false)
                if(!playing) Playback.pauseForConfiguration()
                Playback.suspendForBackground()
                Playback.resumeFromBackground()
                assertEquals("Foreground return must retain the user's playback request",playing,p.playWhenReady)
            } finally {Playback.releaseAll()}
        }
    }
    @Test fun bothLegacyModesDescribeNormalDirectPlaybackAsLive() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("live-controls",Context.MODE_PRIVATE)
        for (legacy in listOf(false,true)) {
            prefs.edit().clear().putBoolean("simple_mode",legacy).commit()
            try {
                Playback.open(context,prefs,listOf(Playable("Live","http://127.0.0.1:9/live.ts",true)),0,null,false)
                assertEquals("LIVE • PAUSE AVAILABLE",Playback.livePathLabel())
                assertFalse(Timeshift.active)
            } finally {Playback.releaseAll()}
        }
    }
    @Test fun activityStartDoesNotRestoreLastChannelBeforeVideoIsVisible() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val context=controller.get()
        try {
            val p=Playback.open(context,context.getSharedPreferences("silent-startup",Context.MODE_PRIVATE),listOf(Playable("Last channel","http://127.0.0.1:9/live.ts",true)),0,null,false)
            Playback.resumeFromBackground()
            assertTrue("Fixture must start an audible live request",p.playWhenReady)
            controller.pause().stop()
            assertFalse(p.playWhenReady)
            controller.start()
            assertFalse("Activity start must not restore audio behind loading or home screens",p.playWhenReady)
            controller.resume()
            assertFalse("No video screen was requested, so startup stays silent",p.playWhenReady)
        } finally {controller.pause().stop().destroy();Playback.releaseAll()}
    }

    @Test fun losingForegroundSilencesPlayerBeforeActivityStop() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val context=controller.get()
        try {
            val p=Playback.open(context,context.getSharedPreferences("silent-handoff",Context.MODE_PRIVATE),listOf(Playable("Last channel","http://127.0.0.1:9/live.ts",true)),0,null,false)
            Playback.resumeFromBackground()
            assertTrue("Fixture must start an audible live request",p.playWhenReady)
            controller.pause()
            assertFalse("YouTube handoff must silence RYZOD before onStop",p.playWhenReady)
            assertFalse("A timer during the handoff cannot revive a hidden player",Playback.prepareCurrentForRecording())
        } finally {controller.stop().destroy();Playback.releaseAll()}
    }

}
