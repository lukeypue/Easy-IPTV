"""Verify every ELF PT_LOAD alignment in an Android APK (all packaged ABIs)."""
import struct
import sys
import zipfile

count = 0
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in apk.namelist():
        if not name.startswith('lib/') or not name.endswith('.so'):
            continue
        data = apk.read(name)
        assert data[:4] == b'\x7fELF', name
        endian = '<' if data[5] == 1 else '>'
        wide = data[4] == 2
        phoff = struct.unpack_from(endian + ('Q' if wide else 'I'), data, 32 if wide else 28)[0]
        entsize, num = struct.unpack_from(endian + 'HH', data, 54 if wide else 42)
        loads = []
        for i in range(num):
            offset = phoff + i * entsize
            if struct.unpack_from(endian + 'I', data, offset)[0] == 1:
                loads.append(struct.unpack_from(endian + ('Q' if wide else 'I'), data, offset + (48 if wide else 28))[0])
        assert loads and min(loads) >= 16384, (name, loads)
        count += 1
assert count, 'APK contained no native libraries'
print(f'{count} native libraries: every ELF load segment is aligned to at least 16 KB')
