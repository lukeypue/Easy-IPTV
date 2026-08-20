from pathlib import Path

path = Path("app/src/main/java/com/easyiptv/player/MainActivity.kt")
text = path.read_text(encoding="utf-8")

old = "@Composable\nprivate fun SearchKey("
new = "@Composable\nprivate fun androidx.compose.foundation.layout.RowScope.SearchKey("
count = text.count(old)
if count != 1:
    raise SystemExit(f"Expected one SearchKey declaration, found {count}")
text = text.replace(old, new, 1)

old_error = 'false && loadError != null -> Box(Modifier.weight(1f)) { ErrorBox(loadError, onRetry) }'
new_error = 'false && loadError != null -> Box(Modifier.weight(1f)) { ErrorBox(loadError ?: "Provider refresh unavailable", onRetry) }'
count = text.count(old_error)
if count != 1:
    raise SystemExit(f"Expected one nullable offline ErrorBox, found {count}")
text = text.replace(old_error, new_error, 1)

path.write_text(text, encoding="utf-8")
print("Applied RowScope and nullable offline fixes")
