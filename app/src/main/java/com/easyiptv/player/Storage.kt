package com.easyiptv.player

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

/* ---------------------------------------------------------------------------
 * SAVED MEDIA STORAGE
 *
 * Rule 1: never CLAIM USB is active until a real create/write/delete probe passes.
 * Rule 2: downloads + permanent recordings may use a proven removable volume.
 * Rule 3: the temporary live DVR is separate from saved media and falls back
 *         safely if the selected drive disappears.
 *
 * Fire OS 7 is Android 9-class. Some portable USB volumes are exposed only as
 * /storage/<volume-id> instead of getExternalFilesDirs()[1]. On API <= 28 we
 * support that normal Android storage path when WRITE_EXTERNAL_STORAGE has been
 * granted. Newer Fire OS versions remain restricted to app-specific volumes
 * exposed by the OS; EZTV never uses ADB/accessibility/root tricks.
 * ------------------------------------------------------------------------- */
object Storage {
    private const val PREF_ENABLED = "ext_storage_enabled"

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_ENABLED, false)
    fun setEnabled(prefs: SharedPreferences, on: Boolean) {
        prefs.edit().putBoolean(PREF_ENABLED, on).apply()
    }

    private fun isWritable(dir: File): Boolean = try {
        if (!dir.exists()) dir.mkdirs()
        val probe = File(dir, ".eztv_write_test")
        probe.writeText("ok")
        val ok = probe.exists() && probe.length() > 0
        probe.delete()
        ok
    } catch (_: Exception) { false }

    private fun appSpecificRemovable(context: Context): File? {
        return try {
            val dirs = context.getExternalFilesDirs(null)
            for (i in 1 until dirs.size) {
                val d = dirs[i] ?: continue
                val removable = runCatching { Environment.isExternalStorageRemovable(d) }.getOrDefault(true)
                if (removable && Environment.getExternalStorageState(d) == Environment.MEDIA_MOUNTED && isWritable(d)) {
                    return d
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun hasLegacyWritePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT <= 28 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Fire OS 7 portable USB fallback. The customer granted the ordinary
     * Android storage permission; we scan only mounted secondary /storage roots,
     * never emulated/internal storage, and create one EZTV folder. */
    private fun legacyPortableUsb(context: Context): File? {
        if (!hasLegacyWritePermission(context)) return null
        return try {
            val roots = File("/storage").listFiles()?.toList().orEmpty()
            for (r in roots) {
                val n = r.name.lowercase()
                if (!r.isDirectory || n == "emulated" || n == "self" || n == "enc_emulated" || n == "sdcard0") continue
                val removable = runCatching { Environment.isExternalStorageRemovable(r) }.getOrDefault(true)
                if (!removable) continue
                val eztv = File(r, "EZTV")
                if (isWritable(eztv)) return eztv
            }
            null
        } catch (_: Exception) { null }
    }

    /** A removable volume that EZTV has PROVEN it can write. */
    fun drive(context: Context): File? = appSpecificRemovable(context) ?: legacyPortableUsb(context)

    /** A physical-looking secondary volume exists, even if Fire OS has not
     * granted EZTV a writable path to it. Used only for honest Settings copy. */
    fun removableDetected(context: Context): Boolean {
        if (drive(context) != null) return true
        return try {
            if (context.getExternalFilesDirs(null).drop(1).any { it != null }) return true
            File("/storage").listFiles()?.any {
                val n = it.name.lowercase()
                it.isDirectory && n != "emulated" && n != "self" && n != "enc_emulated"
            } == true
        } catch (_: Exception) { false }
    }

    fun drivePresent(context: Context): Boolean = drive(context) != null
    fun usingDrive(context: Context, prefs: SharedPreferences): Boolean = isEnabled(prefs) && drivePresent(context)

    /** Saved media destination. */
    fun baseDir(context: Context, prefs: SharedPreferences, category: String): File {
        if (usingDrive(context, prefs)) {
            drive(context)?.let { d ->
                val f = File(d, category)
                runCatching { f.mkdirs() }
                if (f.exists() && isWritable(f)) return f
            }
        }
        val internal = File(context.getExternalFilesDir(null) ?: context.filesDir, category)
        runCatching { internal.mkdirs() }
        return internal
    }

    /** Temporary DVR scratch. If a proven USB drive is enabled, use it to spare
     * Fire Stick eMMC and gain space. Otherwise use cache. Simple/Smooth Live
     * bypasses this file completely. */
    fun timeshiftDir(context: Context, prefs: SharedPreferences): File {
        if (usingDrive(context, prefs)) {
            drive(context)?.let { d ->
                val f = File(d, "timeshift")
                runCatching { f.mkdirs() }
                if (f.exists() && isWritable(f)) return f
            }
        }
        return context.cacheDir
    }

    fun freeBytes(context: Context, prefs: SharedPreferences): Long = try {
        StatFs(baseDir(context, prefs, "downloads").absolutePath).availableBytes
    } catch (_: Exception) { -1L }

    fun internalFreeBytes(context: Context): Long = try {
        StatFs((context.getExternalFilesDir(null) ?: context.filesDir).absolutePath).availableBytes
    } catch (_: Exception) { -1L }

    fun driveFreeBytes(context: Context): Long = try {
        drive(context)?.let { StatFs(it.absolutePath).availableBytes } ?: -1L
    } catch (_: Exception) { -1L }

    fun gb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1_073_741_824.0)
}
