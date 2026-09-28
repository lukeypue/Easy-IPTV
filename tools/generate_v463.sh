set -e
# Diagnostic snapshot before 4.60 patch; harmless and makes generator drift visible.\n          # Historical regeneration commands follow below.\n          python3 tools/fix_v424_patcher_source.py
python3 tools/apply_v424.py
python3 tools/apply_v424_post.py
python3 tools/apply_v425.py
python3 tools/apply_v425_remote_info.py
python3 tools/apply_v426_updater.py
python3 tools/apply_v428.py
python3 tools/apply_v429.py
python3 tools/apply_v430.py
python3 tools/apply_v431.py
python3 - <<'PY'
from pathlib import Path
p=Path('tools/apply_v432.py'); t=p.read_text(); t=t.replace("search_key_marker = '@Composable\\nprivate fun SearchKey('", "search_key_marker = '@Composable\\nprivate fun androidx.compose.foundation.layout.RowScope.SearchKey('"); p.write_text(t)
PY
python3 tools/apply_v432.py
python3 tools/apply_v433.py
python3 tools/apply_v434.py
python3 tools/apply_v435.py
python3 tools/fix_v436_patcher.py
python3 tools/apply_v436.py
python3 tools/apply_v437.py
python3 tools/apply_v438.py
python3 tools/apply_v439.py
python3 tools/fix_v440_patcher.py
python3 tools/apply_v440.py
python3 tools/apply_v441.py
python3 scripts/prepare_v442_generated.py
python3 tools/apply_v443.py
python3 tools/apply_v444.py
python3 tools/apply_v445.py
python3 tools/apply_v446.py
python3 tools/apply_v447.py
python3 tools/apply_v448.py
python3 tools/apply_v448_dvr.py
python3 tools/apply_v448_release.py
python3 tools/apply_v449_core.py
python3 tools/apply_v449_recovery.py
python3 tools/apply_v449_ui.py
python3 tools/apply_v449_screen.py
python3 tools/apply_v449_release.py
python3 tools/apply_v450_universal_guide.py
python3 tools/apply_v450_headroom.py
python3 tools/apply_v450_release.py
python3 tools/apply_v452.py
python3 tools/apply_v453.py
python3 tools/apply_v455.py
python3 tools/apply_v456.py
python3 tools/apply_v457.py
python3 tools/apply_v458.py
python3 tools/apply_v459.py
python3 tools/apply_v460.py
python3 tools/apply_v460_downloads.py
sed -i 's/versionCode = 84/versionCode = 85/; s/versionName = "4.60"/versionName = "4.61"/' app/build.gradle.kts
python3 tools/apply_v462.py
python3 tools/apply_v463.py
