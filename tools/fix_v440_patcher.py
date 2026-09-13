from pathlib import Path

p = Path('tools/apply_v440.py')
t = p.read_text(encoding='utf-8')
old = 'main, n = pattern.subn(ring_runtime, main, count=1)'
new = 'main, n = pattern.subn(lambda _m: ring_runtime, main, count=1)'
if old not in t:
    raise SystemExit('v440 regex replacement target not found')
p.write_text(t.replace(old, new, 1), encoding='utf-8')
print('Fixed v4.40 generator to preserve Kotlin backslash escapes')
