package com.lyra.kisskhtv

import java.net.URI

object UrlPolicy {
    fun isGoogleSignIn(url: String): Boolean = try {
        val host = URI(url).host.orEmpty().lowercase(java.util.Locale.ROOT).trimEnd('.')
        host == "accounts.google.com" || host.endsWith(".accounts.google.com")
    } catch (_: Exception) { false }

    fun isHttps(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }
}
