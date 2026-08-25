from pathlib import Path

path = Path("tools/apply_v424.py")
text = path.read_text(encoding="utf-8")
start = text.index("for needle, name in [")
end = text.index("\ndef patch_player", start)
replacement = r'''replace_once(
    ''' + "'''" + r'''            confirmButton = {
                TextButton(onClick = {
                    askDeleteRecording = false''' + "'''" + r''',
    ''' + "'''" + r'''            confirmButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {
                    askDeleteRecording = false''' + "'''" + r''',
    'recording delete focus'
)
replace_once(
    ''' + "'''" + r'''            dismissButton = {
                TextButton(onClick = {
                    askDeleteRecording = false''' + "'''" + r''',
    ''' + "'''" + r'''            dismissButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {
                    askDeleteRecording = false''' + "'''" + r''',
    'recording keep focus'
)
replace_once(
    ''' + "'''" + r'''            confirmButton = {
                TextButton(onClick = {
                    askDeleteDownload = false''' + "'''" + r''',
    ''' + "'''" + r'''            confirmButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {
                    askDeleteDownload = false''' + "'''" + r''',
    'download delete focus'
)
replace_once(
    ''' + "'''" + r'''            dismissButton = {
                TextButton(onClick = {
                    askDeleteDownload = false''' + "'''" + r''',
    ''' + "'''" + r'''            dismissButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {
                    askDeleteDownload = false''' + "'''" + r''',
    'download keep focus'
)
'''
path.write_text(text[:start] + replacement + text[end:], encoding="utf-8")
print("Fixed recording/download dialog focus patch targets")
