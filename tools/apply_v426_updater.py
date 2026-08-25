from pathlib import Path

PATH = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
text = PATH.read_text(encoding='utf-8')

helper_marker = '@Composable\nfun SettingsPane('
if helper_marker not in text:
    raise SystemExit('SettingsPane marker missing')

helpers = r'''
// ZAKO_V426_NATIVE_UPDATER
private const val ZAKO_RELEASE_CERT_SHA256 = "8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165"

private suspend fun downloadZakoUpdate(
    context: android.content.Context,
    url: String
): java.io.File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val updateDir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
    val target = java.io.File(updateDir, "Zako-update.apk")
    val partial = java.io.File(updateDir, "Zako-update.apk.part")
    partial.delete()

    val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "Zako-Updater/${BuildConfig.VERSION_NAME}")
    }
    try {
        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw java.io.IOException("Update server returned ${connection.responseCode}")
        }
        connection.inputStream.use { input ->
            java.io.FileOutputStream(partial).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
    } finally {
        connection.disconnect()
    }

    if (partial.length() < 1_000_000L) {
        partial.delete()
        throw java.io.IOException("Downloaded update was unexpectedly small")
    }
    if (target.exists()) target.delete()
    if (!partial.renameTo(target)) {
        partial.copyTo(target, overwrite = true)
        partial.delete()
    }
    target
}

@Suppress("DEPRECATION")
private fun validateZakoUpdateApk(context: android.content.Context, apk: java.io.File) {
    val pm = context.packageManager
    val flags = if (android.os.Build.VERSION.SDK_INT >= 28) {
        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        android.content.pm.PackageManager.GET_SIGNATURES
    }
    val info = pm.getPackageArchiveInfo(apk.absolutePath, flags)
        ?: throw java.io.IOException("Android could not read the update package")

    if (info.packageName != context.packageName) {
        throw java.lang.SecurityException("Update package identity did not match Zako")
    }

    val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }
    if (versionCode <= BuildConfig.VERSION_CODE.toLong()) {
        throw java.io.IOException("Downloaded package is not newer than this Zako version")
    }

    val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) {
        info.signingInfo?.apkContentsSigners ?: emptyArray()
    } else {
        info.signatures ?: emptyArray()
    }
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val trusted = signatures.any { signature ->
        md.digest(signature.toByteArray()).joinToString("") { byte -> "%02X".format(byte) } ==
            ZAKO_RELEASE_CERT_SHA256
    }
    if (!trusted) {
        throw java.lang.SecurityException("Update signature did not match the permanent Zako key")
    }
}

private fun canZakoRequestInstall(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

private fun openZakoInstallPermission(context: android.content.Context) {
    val packageUri = android.net.Uri.parse("package:${context.packageName}")
    val specific = android.content.Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        packageUri
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = android.content.Intent(
        android.provider.Settings.ACTION_SECURITY_SETTINGS
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        if (specific.resolveActivity(context.packageManager) != null) {
            context.startActivity(specific)
        } else {
            context.startActivity(fallback)
        }
    }.onFailure {
        runCatching { context.startActivity(fallback) }
    }
}

private fun launchZakoPackageInstaller(context: android.content.Context, apk: java.io.File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apk
    )
    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
        android.content.Intent.FLAG_ACTIVITY_NEW_TASK

    val install = android.content.Intent(android.content.Intent.ACTION_INSTALL_PACKAGE).apply {
        data = uri
        addFlags(flags)
    }
    runCatching {
        context.startActivity(install)
    }.onFailure {
        val fallback = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(flags)
        }
        context.startActivity(fallback)
    }
}

'''

text = text.replace(helper_marker, helpers + helper_marker, 1)

old = r'''            if (updateUrl != null) {
                Chip("Get update", false) {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(updateUrl)
                            )
                        )
                    }.onFailure { toast(ctx, "Couldn't open the update page on this device.") }
                }
            }
'''

new = r'''            if (updateUrl != null) {
                Chip("Get update", false) {
                    val url = updateUrl
                    if (url != null) {
                        if (!canZakoRequestInstall(ctx)) {
                            updateStatus = "Allow Zako to install updates, then return and press Get update again."
                            openZakoInstallPermission(ctx)
                        } else {
                            updateStatus = "Downloading update…"
                            updateScope.launch {
                                val result = runCatching {
                                    val apk = downloadZakoUpdate(ctx, url)
                                    validateZakoUpdateApk(ctx, apk)
                                    apk
                                }
                                result.onSuccess { apk ->
                                    updateStatus = "Download complete — confirm Install on the next screen."
                                    launchZakoPackageInstaller(ctx, apk)
                                }.onFailure {
                                    updateStatus = "Couldn't download or verify the update. Check your connection and try again."
                                }
                            }
                        }
                    }
                }
            }
'''

count = text.count(old)
if count != 1:
    raise SystemExit(f'old browser updater block: expected 1 match, found {count}')
text = text.replace(old, new, 1)

PATH.write_text(text, encoding='utf-8')
print('Applied Zako v4.26 native updater: in-app download + signature validation + package installer')
