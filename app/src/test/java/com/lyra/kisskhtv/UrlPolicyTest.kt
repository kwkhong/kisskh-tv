package com.lyra.kisskhtv

import org.junit.Assert.*
import org.junit.Test

class UrlPolicyTest {
    @Test fun googleSignInHostMatchesOnlyGoogleAccountHosts() {
        listOf("https://accounts.google.com/o/oauth2/auth", "https://ACCOUNTS.GOOGLE.COM./", "https://x.accounts.google.com/")
            .forEach { assertTrue(it, UrlPolicy.isGoogleSignIn(it)) }
        listOf("https://accounts.google.com.evil.test/", "https://evil.test/?next=accounts.google.com", "https://kisskh.co/", "invalid")
            .forEach { assertFalse(it, UrlPolicy.isGoogleSignIn(it)) }
    }

    @Test fun normalHttpsNavigation() {
        assertTrue(UrlPolicy.isHttps("https://kisskh.co/"))
        assertTrue(UrlPolicy.isHttps("https://player.example/video?id=123"))
    }
    @Test fun unsafeOrMalformedNavigationsAreRejected() {
        listOf("http://kisskh.co/", "javascript:alert(1)", "file:///sdcard/test", "content://private",
            "intent://player#Intent;end", "data:text/html,hello", "https:///missing-host", "https://user:pass@host/",
            "https://", "", "not a url").forEach { assertFalse(it, UrlPolicy.isHttps(it)) }
    }
}
