from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')

old = '''                        Text(
                            ch.name,
                            color = Ink,
                            fontSize = 9.sp,
                            fontWeight = if (selectedNow) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )'''
new = '''                        // ZAKO_V445_MINI_EPG: each of the three visible mini-guide rows
                        // shows both the channel and the show airing now.
                        val nowMs = System.currentTimeMillis()
                        val currentShow = EpgStore.guide(ch.epgId, ch.name)
                            .firstOrNull { nowMs in it.startMs until it.endMs }
                            ?.title
                            ?.takeIf { it.isNotBlank() }
                            ?: "Program info unavailable"
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.name,
                                color = Ink,
                                fontSize = 9.sp,
                                fontWeight = if (selectedNow) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                currentShow,
                                color = if (selectedNow) Accent else Muted,
                                fontSize = 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }'''
if main.count(old) != 1:
    raise SystemExit(f'mini-guide channel row expected 1 match, found {main.count(old)}')
main = main.replace(old, new, 1)

# Two text lines need a little more room while still keeping exactly three rows visible.
main = main.replace('modifier = Modifier.fillMaxWidth().height(66.dp)', 'modifier = Modifier.fillMaxWidth().height(102.dp)', 1)
main = main.replace('.height(21.dp)', '.height(33.dp)', 1)
main = main.replace('only 3 rows stay on screen', '3 channels + 3 shows • OK tunes', 1)

# Release identity.
gradle, n1 = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 70', gradle, count=1)
gradle, n2 = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.45"', gradle, count=1)
if n1 != 1 or n2 != 1:
    raise SystemExit('v4.45 version bump failed')

MAIN.write_text(main, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
print('Applied Zako 4.45 mini-guide + navigation regression repair')
