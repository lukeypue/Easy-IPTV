package com.easyiptv.player

import org.json.JSONObject

internal object UpdateDistribution {
    fun downloadUrl(manifest: JSONObject, television: Boolean): String {
        val tvUrl = manifest.optString("downloadUrlTv")
        val url = if(television && tvUrl.isNotBlank()) tvUrl else manifest.optString("downloadUrl")
        check(url.startsWith("https://github.com/lukeypue/Easy-IPTV/releases/download/")) {
            "Update server returned an unexpected download location"
        }
        return url
    }
}
