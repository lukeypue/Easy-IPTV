from pathlib import Path

main = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt').read_text(encoding='utf-8')
manifest = Path('app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
paths = Path('app/src/main/res/xml/zako_update_paths.xml')

required_main = [
    'ZAKO_V426_NATIVE_UPDATER',
    'downloadZakoUpdate(',
    'validateZakoUpdateApk(',
    'canRequestPackageInstalls()',
    'FileProvider.getUriForFile(',
    'Intent.ACTION_INSTALL_PACKAGE',
    'ZAKO_RELEASE_CERT_SHA256',
    'Downloading update…',
]
for needle in required_main:
    if needle not in main:
        raise SystemExit(f'Missing native updater marker: {needle}')

old_browser_block = '''android.content.Intent(\n                                android.content.Intent.ACTION_VIEW,\n                                android.net.Uri.parse(updateUrl)'''
if old_browser_block in main:
    raise SystemExit('Old browser-based updater is still present')

if 'android.permission.REQUEST_INSTALL_PACKAGES' not in manifest:
    raise SystemExit('REQUEST_INSTALL_PACKAGES permission missing')
if 'androidx.core.content.FileProvider' not in manifest:
    raise SystemExit('FileProvider missing from manifest')
if '${applicationId}.fileprovider' not in manifest:
    raise SystemExit('FileProvider authority is not applicationId-scoped')
if not paths.exists():
    raise SystemExit('zako_update_paths.xml missing')
paths_text = paths.read_text(encoding='utf-8')
if '<cache-path' not in paths_text or 'path="updates/"' not in paths_text:
    raise SystemExit('Update cache path is not restricted to updates/')

print('Zako v4.26 native updater verification passed')
