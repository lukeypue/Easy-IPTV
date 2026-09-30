# Zako release shrinker rules. Keep Compose/Media3/library consumer rules authoritative.
# Keep service/receiver entry points named from AndroidManifest.
-keep class com.easyiptv.player.RecordingService { *; }
-keep class com.easyiptv.player.DownloadService { *; }
-keep class com.easyiptv.player.AlarmReceiver { *; }
-keep class com.easyiptv.player.ScheduleRecoveryReceiver { *; }
