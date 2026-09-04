package com.lyra.kisskhtv

import java.net.URI

object UrlPolicy {
    fun isHttps(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }
}
