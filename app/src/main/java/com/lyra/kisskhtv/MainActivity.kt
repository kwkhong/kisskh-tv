package com.lyra.kisskhtv

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {
    companion object { const val HOME_URL = "https://kisskh.co/" }

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: View
    private lateinit var retryButton: Button
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var pointer: RemotePointerView
    private lateinit var navigationScript: String
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var pageFailed = false
    private var retryUrl = HOME_URL
    private var popup: WebView? = null
    private var popupContainer: FrameLayout? = null
    private var popupAddress: TextView? = null
    private var browserNotice: AlertDialog? = null
    private var googlePopupBlocked = false
    private val activeWebView: WebView get() = popup ?: webView
    private var centerDown = false
    private var longPressHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView = findViewById(R.id.webView)
        progress = findViewById(R.id.progress)
        errorPanel = findViewById(R.id.errorPanel)
        retryButton = findViewById(R.id.retryButton)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        pointer = RemotePointerView(this)
        findViewById<FrameLayout>(R.id.root).addView(pointer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        navigationScript = assets.open("tv-navigation.js").bufferedReader().use { it.readText() }
        configureWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenView != null -> exitFullscreen()
                    pointer.pointerActive -> pointer.pointerActive = false
                    popup != null -> closePopup()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })
        retryButton.setOnClickListener { webView.loadUrl(retryUrl); webView.requestFocus() }
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(HOME_URL)
        }
        webView.requestFocus()
        immersive()
        Toast.makeText(this, R.string.remote_help, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(target: WebView = webView) {
        val webView = target
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (UrlPolicy.isGoogleSignIn(url.orEmpty())) {
                    view.stopLoading()
                    showBrowserSignInNotice()
                    return
                }
                if (view !== this@MainActivity.webView) {
                    popupAddress?.text = Uri.parse(url.orEmpty()).host ?: getString(R.string.popup_blank)
                    return
                }
                pageFailed = false
                if (url != null && UrlPolicy.isHttps(url)) retryUrl = url
                errorPanel.visibility = View.GONE
                progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (view === this@MainActivity.webView) progress.visibility = View.GONE
                // A cancelled previous load can report an error while the replacement
                // document is loading. Navigation installation belongs to the document,
                // not to the previous request's error state.
                view.evaluateJavascript(navigationScript, null)
            }
            override fun onPageCommitVisible(view: WebView, url: String?) {
                view.evaluateJavascript(navigationScript, null)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (UrlPolicy.isGoogleSignIn(request.url.toString())) {
                    showBrowserSignInNotice()
                    return true
                }
                // Only the blank document used to establish a popup opener is allowed.
                if (view === popup && request.url.toString() == "about:blank") return false
                // Normal HTTPS browsing only. Never launch arbitrary intents or file/content URLs.
                return !UrlPolicy.isHttps(request.url.toString())
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    if (view === this@MainActivity.webView) showError()
                    else Toast.makeText(this@MainActivity, R.string.popup_load_error, Toast.LENGTH_LONG).show()
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) {
                    if (view === this@MainActivity.webView) showError()
                    else Toast.makeText(this@MainActivity, R.string.popup_load_error, Toast.LENGTH_LONG).show()
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (view === this@MainActivity.webView && (error.url == view.url || error.url == retryUrl)) showError()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (view !== this@MainActivity.webView) return
                progress.visibility = if (!pageFailed && newProgress < 100) View.VISIBLE else View.GONE
            }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (webView === popup || fullscreenView != null) { callback.onCustomViewHidden(); return }
                fullscreenView = view
                fullscreenCallback = callback
                webView.visibility = View.GONE
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(view, FrameLayout.LayoutParams(-1, -1))
                view.requestFocus()
                immersive()
            }
            override fun onHideCustomView() = exitFullscreen()
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                // Keep the opener document alive for Firebase postMessage/window.closed.
                // Only one user-requested window; no automatic or nested popups.
                if (!isUserGesture || popup != null || view !== this@MainActivity.webView || fullscreenView != null) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                googlePopupBlocked = false
                val child = WebView(this@MainActivity)
                popup = child
                configureWebView(child)
                val panel = FrameLayout(this@MainActivity)
                val headerHeight = (40 * resources.displayMetrics.density).toInt()
                val address = TextView(this@MainActivity).apply {
                    text = getString(R.string.popup_blank)
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.DKGRAY)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(16, 0, 16, 0)
                }
                popupContainer = panel
                popupAddress = address
                panel.addView(child, FrameLayout.LayoutParams(-1, -1).apply { topMargin = headerHeight })
                panel.addView(address, FrameLayout.LayoutParams(-1, headerHeight))
                findViewById<FrameLayout>(R.id.root).addView(panel, FrameLayout.LayoutParams(-1, -1))
                pointer.bringToFront()
                pointer.pointerActive = false
                child.requestFocus()
                transport.webView = child
                resultMsg.sendToTarget()
                Toast.makeText(this@MainActivity, R.string.popup_help, Toast.LENGTH_LONG).show()
                return true
            }
            override fun onCloseWindow(window: WebView) {
                if (window === popup) closePopup()
            }
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                if (googlePopupBlocked && message.contains("auth/popup-closed-by-user")) {
                    // The native explanation already describes why this login was stopped.
                    googlePopupBlocked = false
                    result.confirm()
                    return true
                }
                return super.onJsAlert(view, url, message, result)
            }
            // Do not grant camera, microphone or protected-media identifiers.
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(this, R.string.downloads_disabled, Toast.LENGTH_SHORT).show()
        }
    }

    private fun closePopup() {
        val child = popup ?: return
        popup = null
        pointer.pointerActive = false
        popupContainer?.let { (it.parent as? ViewGroup)?.removeView(it) }
        popupContainer = null
        popupAddress = null
        (child.parent as? ViewGroup)?.removeView(child)
        child.stopLoading()
        child.destroy()
        webView.requestFocus()
    }

    private fun showBrowserSignInNotice() {
        googlePopupBlocked = true
        // Post removal: never destroy a WebView from within its navigation callback.
        webView.post {
            if (isDestroyed || isFinishing) return@post
            closePopup()
            if (browserNotice?.isShowing == true) return@post
            browserNotice = AlertDialog.Builder(this)
                .setTitle(R.string.google_sign_in_title)
                .setMessage(R.string.google_sign_in_message)
                .setPositiveButton(R.string.open_browser) { _, _ ->
                    try {
                        // Open the site from the beginning in the browser's own session.
                        // Do not transfer OAuth tokens, credentials or WebView cookies.
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HOME_URL)).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, R.string.browser_missing, Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(R.string.keep_watching, null)
                .show()
        }
    }

    private fun showError() {
        pageFailed = true
        progress.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
        retryButton.requestFocus()
    }

    private fun immersive() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun exitFullscreen() {
        val current = fullscreenView ?: return
        val callback = fullscreenCallback
        fullscreenView = null
        fullscreenCallback = null
        fullscreenContainer.removeView(current)
        fullscreenContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        callback?.onCustomViewHidden()
        webView.requestFocus()
        immersive()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::webView.isInitialized || (popup == null && errorPanel.visibility == View.VISIBLE)) return super.dispatchKeyEvent(event)
        val webView = activeWebView
        // Leave the on-screen keyboard's keys to Android.
        if (ViewCompat.getRootWindowInsets(webView)?.isVisible(WindowInsetsCompat.Type.ime()) == true) return super.dispatchKeyEvent(event)
        val key = event.keyCode
        val center = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
        if (center) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) { centerDown = true; longPressHandled = false }
                if (centerDown && !longPressHandled && event.eventTime - event.downTime >= 600) {
                    pointer.pointerActive = !pointer.pointerActive
                    longPressHandled = true
                    Toast.makeText(this, if (pointer.pointerActive) R.string.pointer_on else R.string.pointer_off,
                        Toast.LENGTH_SHORT).show()
                }
            } else if (event.action == KeyEvent.ACTION_UP && centerDown) {
                centerDown = false
                if (!longPressHandled && !event.isCanceled) {
                    when {
                        pointer.pointerActive -> clickPointer()
                        fullscreenView != null -> {
                            fullscreenView?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                            fullscreenView?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                        }
                        else -> webView.evaluateJavascript(navigationScript + "\nwindow.__kissKhTvActivate && window.__kissKhTvActivate()") { result ->
                            if (result != null && result != "null" && !isDestroyed) {
                                try {
                                    val point = JSONObject(result)
                                    sendTouch(webView, point.getDouble("x").toFloat() * webView.width,
                                        point.getDouble("y").toFloat() * webView.height)
                                } catch (_: org.json.JSONException) { /* Page changed during selection. */ }
                            }
                        }
                    }
                }
            }
            return true
        }
        val direction = when (key) {
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            else -> null
        }
        if (direction != null && (pointer.pointerActive || fullscreenView == null)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (pointer.pointerActive) {
                    val delta = (if (event.repeatCount > 5) 28 else 14) * resources.displayMetrics.density
                    pointer.move(direction, delta)
                } else webView.evaluateJavascript(navigationScript + "\nwindow.__kissKhTvMove && window.__kissKhTvMove('$direction')", null)
            }
            return true
        }
        // Native media keys are forwarded to fullscreen players, including cross-origin frames.
        return super.dispatchKeyEvent(event)
    }

    private fun clickPointer() {
        val target: View = if (fullscreenView != null) fullscreenContainer else activeWebView
        val pointerOrigin = IntArray(2)
        val targetOrigin = IntArray(2)
        pointer.getLocationInWindow(pointerOrigin)
        target.getLocationInWindow(targetOrigin)
        sendTouch(target, pointer.cursorX + pointerOrigin[0] - targetOrigin[0],
            pointer.cursorY + pointerOrigin[1] - targetOrigin[1])
    }

    private fun sendTouch(target: View, x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite() || x < 0 || y < 0 || x > target.width || y > target.height) return
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val touch = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0)
            target.dispatchTouchEvent(touch)
            touch.recycle()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) { webView.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onPause() { popup?.onPause(); webView.onPause(); CookieManager.getInstance().flush(); super.onPause() }
    override fun onResume() { super.onResume(); if (::webView.isInitialized) webView.onResume(); popup?.onResume() }
    override fun onDestroy() {
        browserNotice?.dismiss()
        closePopup()
        exitFullscreen()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
