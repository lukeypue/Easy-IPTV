#!/usr/bin/env python3
from pathlib import Path
p=Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
s=p.read_text()
if 'ZAKO_V449_MANAGED_DVR_SCREEN' in s:
    print('4.49 screen integration already applied'); raise SystemExit(0)
marker='@Composable\nfun SettingsPane('
if marker not in s: raise SystemExit('SettingsPane anchor missing')
block=r'''
/* ZAKO_V449_MANAGED_DVR_SCREEN
 * Recordings screen integration: the existing Recordings view can call this
 * lightweight pane above completed recordings. Manual date/time entry is
 * intentionally independent of provider EPG horizon.
 */
@Composable
fun ManagedDvrSchedulePane(
    prefs: android.content.SharedPreferences,
    channels: List<Channel>,
    refreshToken: Int,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val rows = remember(refreshToken) { ManagedDvrUi.upcoming(prefs) }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(ManagedDvrUi.upcomingLabel, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (rows.isEmpty()) Text("No future recordings scheduled.", color = Muted, fontSize = 11.sp)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.title, color = Ink, fontWeight = FontWeight.Bold)
                    Text(row.channel + " • " + row.date + " • " + row.start + " – " + row.end + " • " + row.status, color = Muted, fontSize = 10.sp)
                }
                Chip(ManagedDvrUi.editLabel, false) {
                    val existing = ScheduleStore.load(prefs).firstOrNull { it.id == row.id }
                    if (existing != null) {
                        toast(context, ManagedDvrUi.edit(context, prefs, existing.id, existing.title, existing.channelName, existing.url, existing.startMs, existing.endMs))
                        onRefresh()
                    }
                }
                Chip(ManagedDvrUi.cancelLabel, false) {
                    ManagedDvrUi.cancel(context, prefs, row.id)
                    onRefresh()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(ManagedDvrUi.manualLabel, color = Ink, fontWeight = FontWeight.Bold)
        Text("Choose a channel and enter a future start/end time even when the provider guide does not reach that far.", color = Muted, fontSize = 10.sp)
        if (channels.isNotEmpty()) {
            Chip("Manual Recording", false) {
                val ch = channels.first()
                val start = System.currentTimeMillis() + 60L * 60L * 1000L
                val end = start + 60L * 60L * 1000L
                toast(context, ManagedDvrUi.manualRecording(context, prefs, "", ch.name, ch.url, start, end))
                onRefresh()
            }
        }
    }
}

'''
s=s.replace(marker,block+marker,1)
p.write_text(s)
print('Applied Zako 4.49 Recordings screen integration')
