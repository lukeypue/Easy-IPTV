package com.easyiptv.player

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import java.io.File

/* ---------------------------------------------------------------------------
 * EXTERNAL DRIVE STORAGE
 * Fire OS won't let us fuse a USB/SSD into internal storage as one pool
 * (Amazon disabled adoptable storage). But we CAN write downloads, recordings,
 * and the DVR timeshift file straight onto the plugged-in drive — so the 16 GB
 * internal never fills up. getExternalFilesDirs() returns app-private folders:
 * slot 0 = internal, slot 1+ = removable drives (the "Amazon side thingie").
 *
 * Safety rules baked in:
 *  - Use the drive only if the customer turned it on AND it's actually present.
 *  - If the drive is missing/unplugged, silently fall back to internal so
 *    nothing crashes or freezes (we know how touchy the DVR path is).
 *  - Timeshift (fast, constant writes) also goes to the drive when enabled —
 *    that's the biggest space win.
 * ------------------------------------------------------------------------- */
object Storage {
    private const val PREF_ENABLED = "ext_storage_enabled"

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_ENABLED, false)

    fun setEnabled(prefs: SharedPreferences, on: Boolean) {
        prefs.edit().putBoolean(PREF_ENABLED, on).apply()
    }

    /** The removable drive's app folder if one is plugged in, else null.
     *  Slot 0 is internal; any later slot that exists and is removable is the drive. */
    fun drive(context: Context): File? {
        return try {
            val dirs = context.getExternalFilesDirs(null)
            if (dirs.size < 2) return null
            for (i in 1 until dirs.size) {
                val d = dirs[i] ?: continue
                // Must exist / be creatable and be reported as removable.
                if (!d.exists()) d.mkdirs()
                if (d.exists() && android.os.Environment.isExternalStorageRemovable(d)) {
                    return d
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Is a usable removable drive present right now? */
    fun drivePresent(context: Context): Boolean = drive(context) != null

    /** True when we should actually be writing to the drive (on + present). */
    fun usingDrive(context: Context, prefs: SharedPreferences): Boolean =
        isEnabled(prefs) && drivePresent(context)

    /** Base folder for a category ("downloads" / "recordings"), on the drive if
     *  active, otherwise internal. Always returns a real, creatable folder. */
    fun baseDir(context: Context, prefs: SharedPreferences, category: String): File {
        if (usingDrive(context, prefs)) {
            drive(context)?.let { d ->
                val f = File(d, category)
                runCatching { f.mkdirs() }
                if (f.exists()) return f
            }
        }
        val internal = File(context.getExternalFilesDir(null) ?: context.filesDir, category)
        runCatching { internal.mkdirs() }
        return internal
    }

    /** Where the DVR timeshift scratch file lives. Drive if active, else cache. */
    fun timeshiftDir(context: Context, prefs: SharedPreferences): File {
        if (usingDrive(context, prefs)) {
            drive(context)?.let { d ->
                val f = File(d, "timeshift")
                runCatching { f.mkdirs() }
                if (f.exists()) return f
            }
        }
        return context.cacheDir
    }

    /** Free bytes on whichever storage is active for saved media, or -1. */
    fun freeBytes(context: Context, prefs: SharedPreferences): Long = try {
        StatFs(baseDir(context, prefs, "downloads").absolutePath).availableBytes
    } catch (e: Exception) {
        -1L
    }

    /** Free bytes on the internal storage (for the settings comparison). */
    fun internalFreeBytes(context: Context): Long = try {
        StatFs((context.getExternalFilesDir(null) ?: context.filesDir).absolutePath).availableBytes
    } catch (e: Exception) {
        -1L
    }

    /** Free bytes on the plugged-in drive, or -1 if none. */
    fun driveFreeBytes(context: Context): Long = try {
        drive(context)?.let { StatFs(it.absolutePath).availableBytes } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    fun gb(bytes: Long): String =
        String.format(java.util.Locale.US, "%.1f", bytes / 1_073_741_824.0)

    /** exFAT/NTFS handle big files; FAT32 caps at 4 GB per file, which matters
     *  for long recordings. Best-effort guess so we can warn the customer. */
    fun driveLikelyFat32(context: Context): Boolean {
        val d = drive(context) ?: return false
        return try {
            // FAT32 volumes are almost always <= 32 GB when formatted by consumer
            // tools, and always report <= ~2 TB. We can't read the FS type from an
            // app folder directly, so we treat a total size <= 32 GB as "probably
            // FAT32" for the warning only. exFAT drives are usually bigger.
            val total = StatFs(d.absolutePath).totalBytes
            total in 1..(34_359_738_368L) // ~32 GB
        } catch (e: Exception) {
            false
        }
    }
}
