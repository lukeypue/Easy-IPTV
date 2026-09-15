from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
GRADLE = Path('app/build.gradle.kts')
main = MAIN.read_text()
gradle = GRADLE.read_text()


def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1, found {n}')
    return text.replace(old, new, 1)


gradle, n = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 60', gradle, count=1)
if n != 1:
    raise SystemExit('version code')
gradle, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.35"', gradle, count=1)
if n != 1:
    raise SystemExit('version name')

old = '''                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.name,
                                color = Ink, fontSize = 12.sp,
                                fontWeight = if (selectedNow) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (rowProgram != null) {
                                Text(
                                    rowProgram.title,
                                    color = Color(0xFFFFE45C), fontSize = 11.sp, fontWeight = FontWeight.Bold, // ZAKO_V432_MINI_YELLOW
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
'''

new = '''                        // ZAKO_V435_LINEUP_HORIZONTAL: the three visible mini-guide rows keep
                        // the channel and current program side-by-side so the program title never
                        // drops under the channel and gets vertically clipped by the 38dp row.
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ch.name,
                                color = Ink, fontSize = 12.sp,
                                fontWeight = if (selectedNow) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(if (rowProgram != null) 0.42f else 1f)
                            )
                            if (rowProgram != null) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    rowProgram.title,
                                    color = Color(0xFFFFE45C), fontSize = 11.sp, fontWeight = FontWeight.Bold, // ZAKO_V432_MINI_YELLOW
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.58f)
                                )
                            }
                        }
'''

main = once(main, old, new, 'mini lineup horizontal layout')
MAIN.write_text(main)
GRADLE.write_text(gradle)
print('applied v4.35')
