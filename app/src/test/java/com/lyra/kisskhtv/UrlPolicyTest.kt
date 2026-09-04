package com.lyra.kisskhtv

import org.junit.Assert.*
import org.junit.Test

class UrlPolicyTest {
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
