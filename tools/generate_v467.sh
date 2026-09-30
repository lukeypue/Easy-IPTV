#!/usr/bin/env bash
set -euo pipefail
# Reproduce the locked baseline before applying the narrow 4.67 changes.
bash tools/generate_v463.sh
python3 tools/apply_v464.py
python3 tools/prepare_v465_tests.py
python3 tools/apply_v465.py
python3 tools/apply_v466.py
python3 tools/apply_v467.py
python3 tools/apply_v467_memory.py
python3 tools/apply_v467_review.py
python3 tools/apply_v467_epg.py
python3 tools/apply_v467_icons.py

python3 - <<'PY'
from pathlib import Path
p=Path('app/build.gradle.kts')
s=p.read_text()
s=s.replace('versionCode = 91','versionCode = 92',1).replace('versionName = "4.67"','versionName = "4.68"',1)
p.write_text(s)
PY
