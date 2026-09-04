package com.lyra.kisskhtv

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TvAppTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun js(scenario: ActivityScenario<MainActivity>, expression: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        scenario.onActivity { it.findViewById<WebView>(R.id.webView).evaluateJavascript(expression) { value ->
            result = value; latch.countDown()
        } }
        assertTrue("JavaScript callback timed out", latch.await(10, TimeUnit.SECONDS))
        return result
    }
    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }
    private fun fixture(scenario: ActivityScenario<MainActivity>, html: String) {
        scenario.onActivity {
            val web = it.findViewById<WebView>(R.id.webView)
            web.stopLoading()
            web.loadDataWithBaseURL("https://example.test/", "<meta name='viewport' content='width=device-width,initial-scale=1'>" + html,
                "text/html", "UTF-8", null)
        }
        waitFor("Fixture and navigation script failed to load") {
            js(scenario, "document.readyState === 'complete' && !!window.__kissKhTvMove") == "true"
        }
    }
    private fun key(code: Int) = instrumentation.sendKeyDownUpSync(code)

    @Test fun tvLauncherAndSecureWebView() {
        val context = instrumentation.targetContext
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(tvIntent, PackageManager.MATCH_DEFAULT_ONLY or 0).isNotEmpty()
            || context.packageManager.queryIntentActivities(tvIntent, 0).isNotEmpty())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val web = it.findViewById<WebView>(R.id.webView)
                assertTrue(web.settings.javaScriptEnabled)
                assertTrue(web.settings.domStorageEnabled)
                assertFalse(web.settings.allowFileAccess)
                assertFalse(web.settings.allowContentAccess)
                assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.settings.mixedContentMode)
                assertEquals(MainActivity.HOME_URL, web.url ?: web.originalUrl)
            }
        }
    }

    @Test fun dpadMovesAndOkActivatesExactlyOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fixture(scenario, """<style>button{position:absolute;top:100px;width:100px;height:60px}</style>
                <button id='one' style='left:50px'>One</button>
                <button id='two' style='left:250px' onclick='window.clicks=(window.clicks||0)+1'>Two</button>
                <button id='disabled' disabled style='left:180px'>Disabled</button>""")
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            waitFor("Initial focus") { js(scenario, "document.activeElement.id") == "\"one\"" }
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitFor("Spatial focus should skip disabled button") { js(scenario, "document.activeElement.id") == "\"two\"" }
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            waitFor("OK must click once") { js(scenario, "window.clicks") == "1" }
        }
    }

    @Test fun longOkEnablesPointerAndBackDisablesIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fixture(scenario, "<button onclick='window.clicked=true'>Click</button>")
            scenario.onActivity {
                val t = SystemClock.uptimeMillis()
                it.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                it.dispatchKeyEvent(KeyEvent(t, t + 700, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1))
                it.dispatchKeyEvent(KeyEvent(t, t + 720, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                val root = it.findViewById<android.widget.FrameLayout>(R.id.root)
                val pointer = root.getChildAt(root.childCount - 1) as RemotePointerView
                assertTrue(pointer.enabled)
                it.onBackPressedDispatcher.onBackPressed()
                assertFalse(pointer.enabled)
            }
            assertEquals("null", js(scenario, "window.clicked"))
        }
    }

    @Test fun html5VideoPlaysEntersFullscreenAndBackRestoresPage() {
        val video = instrumentation.context.assets.open("test-video.mp4").use { it.readBytes() }
        val base64 = android.util.Base64.encodeToString(video, android.util.Base64.NO_WRAP)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fixture(scenario, """<video id='video' loop playsinline src='data:video/mp4;base64,$base64'></video>
                <button id='play' onclick="video.play();video.requestFullscreen()">Play fullscreen</button>""")
            waitFor("Video should decode") { js(scenario, "video.readyState >= 2") == "true" }
            // DOM focus activation invokes the same visible button used by the app's OK handler.
            js(scenario, "document.getElementById('play').focus()")
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            waitFor("Video should play after OK") { js(scenario, "!video.paused && video.currentTime > 0") == "true" }
            waitFor("HTML5 fullscreen should open native container") {
                var full = false
                scenario.onActivity { full = it.findViewById<View>(R.id.fullscreenContainer).visibility == View.VISIBLE }
                full
            }
            key(KeyEvent.KEYCODE_BACK)
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.fullscreenContainer).visibility)
                assertEquals(View.VISIBLE, it.findViewById<WebView>(R.id.webView).visibility)
            }
        }
    }

    @Test fun networkFailureOffersRemoteFocusableRetry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fixture(scenario, "<button>Ready</button>")
            scenario.onActivity { it.findViewById<WebView>(R.id.webView).loadUrl("https://127.0.0.1:1/") }
            waitFor("Main frame error must show retry") {
                var shown = false
                scenario.onActivity { shown = it.findViewById<View>(R.id.errorPanel).visibility == View.VISIBLE }
                shown
            }
            scenario.onActivity { assertTrue(it.findViewById<Button>(R.id.retryButton).hasFocus()) }
        }
    }

    @Test fun unsafeNavigationCannotEscapeTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fixture(scenario, "<button>Ready</button>")
            scenario.onActivity {
                val web = it.findViewById<WebView>(R.id.webView)
                for (url in listOf("intent://example", "file:///sdcard/data", "http://example.com")) {
                    val request = object : WebResourceRequest {
                        override fun getUrl() = Uri.parse(url)
                        override fun isForMainFrame() = true
                        override fun isRedirect() = false
                        override fun hasGesture() = true
                        override fun getMethod() = "GET"
                        override fun getRequestHeaders() = emptyMap<String, String>()
                    }
                    assertTrue(web.webViewClient.shouldOverrideUrlLoading(web, request))
                }
            }
        }
    }
}
