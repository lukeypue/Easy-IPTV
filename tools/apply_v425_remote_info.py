from pathlib import Path

path = Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
text = path.read_text(encoding="utf-8")

old = '''            .onPreviewKeyEvent { ev ->\n                if (onDownload == null) return@onPreviewKeyEvent false\n                val ne = ev.nativeKeyEvent\n                val center = ne.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||\n                    ne.keyCode == android.view.KeyEvent.KEYCODE_ENTER\n'''
new = '''            .onPreviewKeyEvent { ev ->\n                val ne = ev.nativeKeyEvent\n                // ZAKO_V425_REMOTE_INFO: keep poster Left/Right deterministic,\n                // but let TV remotes open the visible ⓘ using MENU/INFO.\n                val infoKey = ne.keyCode == android.view.KeyEvent.KEYCODE_MENU ||\n                    ne.keyCode == android.view.KeyEvent.KEYCODE_INFO\n                if (ne.action == android.view.KeyEvent.ACTION_DOWN &&\n                    ne.repeatCount == 0 && infoKey && onInfo != null) {\n                    onInfo()\n                    return@onPreviewKeyEvent true\n                }\n                if (onDownload == null) return@onPreviewKeyEvent false\n                val center = ne.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||\n                    ne.keyCode == android.view.KeyEvent.KEYCODE_ENTER\n'''

count = text.count(old)
if count != 1:
    raise SystemExit(f"remote Info key handler: expected 1 match, found {count}")
text = text.replace(old, new, 1)

old_help = '"OK plays • Hold OK downloads • Tap ⓘ for Info"'
new_help = '"OK plays • MENU/INFO shows details • Hold OK downloads"'
if text.count(old_help) != 1:
    raise SystemExit("movie helper text marker missing")
text = text.replace(old_help, new_help, 1)

path.write_text(text, encoding="utf-8")
print("Applied v4.25 TV-remote movie Info access")
