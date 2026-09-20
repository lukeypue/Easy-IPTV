#!/usr/bin/env python3
from pathlib import Path
base=Path('app/src/main/java/com/easyiptv/player')
(base/'ScheduleRecoveryReceiver.kt').write_text(r'''package com.easyiptv.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE)
        ScheduleStore.rearmAll(context, prefs)
    }
}
''')
p=Path('app/src/main/AndroidManifest.xml'); m=p.read_text()
perm='<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />'
if perm not in m:
    marker='<uses-permission android:name="android.permission.WAKE_LOCK" />'
    m=m.replace(marker, marker+'\n    '+perm,1)
receiver=r'''        <receiver
            android:name=".ScheduleRecoveryReceiver"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
        </receiver>
'''
if '.ScheduleRecoveryReceiver' not in m:
    m=m.replace('    </application>',receiver+'    </application>',1)
p.write_text(m)
print('Applied Zako 4.49 schedule recovery receiver')
