from pathlib import Path
import unittest

s=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text()
class LiveSearchContracts(unittest.TestCase):
    def test_priority_press_consumes_release_before_views(self):
        dispatch=s[s.index('override fun dispatchKeyEvent'):s.index('override fun onKeyDown')]
        self.assertIn('consumedRemoteKeys.remove(event.keyCode)',dispatch)
    def test_live_ok_is_intercepted_before_the_player_view(self):
        priority=s[s.index('PlayerKeys.priority = {'):s.index('PlayerKeys.handler = {',s.index('PlayerKeys.priority = {'))]
        self.assertIn('KEYCODE_DPAD_CENTER',priority)
        self.assertIn('miniGuideOpen = true',priority)
        self.assertNotIn('togglePlaying()',priority)
    def test_search_bottom_has_a_results_exit(self):
        panel=s[s.index('private fun RyzodKeyboardPanel('):s.index('private fun RyzodKeyboardDialog(')]
        self.assertIn('onBottomExit',panel)
        self.assertIn('row == rows.lastIndex',panel)
    def test_stall_recovery_does_not_depend_on_optional_steady_setting(self):
        governor=s[s.index('private val governorTick'):s.index('private fun startGovernor')]
        self.assertNotIn('if (steadyRecovery && playStateC',governor)
        self.assertNotIn('p.seekTo(',governor)
    def test_no_legacy_mode_switch_offered(self):
        self.assertNotIn('Chip("DVR Live"',s)
        self.assertNotIn('"DVR RETRY"',s)
    def test_failed_pause_setup_preserves_play_request(self):
        self.assertTrue('zapTo(currentIdxC.intValue, startPlaying = pauseResumeRequested)' in s,
                        'Pause setup fallback currently resumes unconditionally')
    def test_recording_keeps_priming_after_initial_timeout(self):
        self.assertTrue('if (!ready && waited >= 15_000L && Recorder.activeName.value == null)' in s,
                        'Recording reader is stranded after initial timeout')
    def test_recording_live_button_resumes_playback(self):
        method=s[s.index('    fun returnToLive()'):s.index('    fun togglePlaying()')]
        self.assertTrue('setPlaying(true)' in method,'LIVE retains the paused state')
    def test_automatic_retry_preserves_paused_intent(self):
        self.assertTrue('startPlaying: Boolean = if (preserveDirect) player?.playWhenReady ?: true else true' in s,
                        'Automatic direct retries currently resume a paused stream')
    def test_live_window_recovery_does_not_force_play(self):
        branch=s[s.index('if (liveMode && error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)'):]
        branch=branch[:branch.index('return')]
        self.assertFalse('p.playWhenReady = true' in branch,'Live-window retry currently resumes paused playback')
    def test_background_resume_remembers_intent(self):
        self.assertTrue('backgroundResumePlaying' in s,'Background/foreground currently discards paused intent')
if __name__=='__main__': unittest.main()
