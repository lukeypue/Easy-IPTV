"""Fail a release if the full JUnit suite was filtered, skipped, or failed."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

reports = list(Path(sys.argv[1]).glob('TEST-*.xml'))
assert reports, 'No JUnit reports were generated'
totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, '0'))
assert totals['tests'] >= int(sys.argv[2]), totals
assert not any(totals[key] for key in ('failures', 'errors', 'skipped')), totals
print('Full regression results:', json.dumps(totals, sort_keys=True))
