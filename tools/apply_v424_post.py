from pathlib import Path

path = Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
text = path.read_text(encoding="utf-8")
old = "@Composable\nprivate fun SearchKey("
new = "@Composable\nprivate fun androidx.compose.foundation.layout.RowScope.SearchKey("
count = text.count(old)
if count != 1:
    raise SystemExit(f"Expected one SearchKey declaration, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Applied RowScope fix for custom TV keyboard")
