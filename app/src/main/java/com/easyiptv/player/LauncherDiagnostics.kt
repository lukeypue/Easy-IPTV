package com.easyiptv.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Read-only evidence from the installed APK, independent of Amazon's tile cache. */
internal object LauncherDiagnostics {
    fun report(context: Context): String = buildString {
        val pm = context.packageManager
        val config = context.resources.configuration
        val app = pm.getApplicationInfo(context.packageName, 0)
        val activity = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        appendLine("RYZOD ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) / ${BuildConfig.DISTRIBUTION}")
        val installed = pm.getPackageInfo(context.packageName, 0)
        appendLine("Package: ${context.packageName}; installed ${installed.versionName}; updated ${java.util.Date(installed.lastUpdateTime)}")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY}")
        appendLine("uiMode=${config.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK}; density=${context.resources.displayMetrics.densityDpi}")
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        val homePackage = home?.activityInfo?.packageName
        val homeVersion = homePackage?.let { runCatching { pm.getPackageInfo(it, 0).versionName }.getOrNull() }
        appendLine("Home: ${homePackage ?: "unavailable"} ${homeVersion.orEmpty()}")
        fun artwork(label: String, id: Int) {
            appendLine(runCatching {
                val value = android.util.TypedValue()
                context.resources.getValue(id, value, true)
                val drawable = context.getDrawable(id)
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                val description = if(bitmap == null) drawable?.javaClass?.simpleName ?: "missing" else {
                    val pixels = ByteBuffer.allocate(bitmap.byteCount)
                    bitmap.copyPixelsToBuffer(pixels)
                    val digest = MessageDigest.getInstance("SHA-256").digest(pixels.array())
                        .take(8).joinToString("") { "%02x".format(it) }
                    "${bitmap.width}x${bitmap.height} sha256=$digest"
                }
                "$label: $description\n${context.resources.getResourceName(id)} → ${value.string}"
            }.getOrElse { "$label: unavailable (${it.javaClass.simpleName})" })
        }
        artwork("app.icon", app.icon)
        artwork("app.banner", app.banner)
        artwork("activity.icon", activity.icon)
        artwork("activity.logo", activity.logo)
        artwork("activity.banner", activity.banner)
        for(category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val resolved = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(context.packageName), 0)
            val label = category.substringAfterLast('.')
            appendLine("$label: ${resolved?.activityInfo?.name ?: "missing"}")
            resolved?.let { artwork("$label.icon", it.icon.takeIf { id -> id != 0 } ?: it.activityInfo.icon) }
        }
        appendLine("This checks installed artwork. Fire TV keeps its own home-screen tile; it may differ.")
    }
}
