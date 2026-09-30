from pathlib import Path
import unittest

ROOT = Path('app/src/main/java/com/easyiptv/player')

class ReleaseContracts(unittest.TestCase):
    def test_guide_does_not_repeat_program_per_half_hour(self):
        s = (ROOT / 'MainActivity.kt').read_text()
        grid = s[s.index('private fun LiveGridGuide('):s.index('private fun chIndexOf(')]
        self.assertNotIn('val slotStart = windowStart + slot * halfHour', grid)
        self.assertIn('GuideGeometry.cells', grid)

    def test_tuning_does_not_start_disk_dvr(self):
        s = (ROOT / 'MainActivity.kt').read_text()
        tune = s[s.index('    fun zapTo(idx:'):s.index('    fun open(', s.index('    fun zapTo(idx:'))]
        self.assertNotIn('Timeshift.start(', tune)

    def test_legacy_release_cannot_undo_manifest_repair(self):
        s = Path('.github/workflows/release454.yml').read_text()
        self.assertNotIn('branches: [ "main" ]', s)

if __name__ == '__main__':
    unittest.main()
