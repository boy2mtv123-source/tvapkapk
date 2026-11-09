package com.rinshal.linkhub

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

// >>> CHANGE THIS to your hosted website link (keep the https:// and the trailing /) <<<
private const val SITE_URL = "https://boy2mtv123-source.github.io/tvapkapk/"

private const val OFFLINE_URL = "file:///android_asset/offline.html"
private const val RELOAD_AFTER_MS = 10 * 60 * 1000L

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var inApp = false
    private var pausedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.isFocusable = true
        web.isFocusableInTouchMode = true

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.cacheMode = WebSettings.LOAD_NO_CACHE
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.mediaPlaybackRequiresUserGesture = false

        web.addJavascriptInterface(Bridge(), "LinkHub")
        web.webViewClient = Client()

        setContentView(web)
        web.requestFocus()
        web.loadUrl(SITE_URL)
    }

    private fun sameSite(url: String?): Boolean {
        val host = Uri.parse(url ?: return false).host ?: return false
        return host == Uri.parse(SITE_URL).host
    }

    private fun openExternal(url: String) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // no browser on the TV: show it inside this app instead
            inApp = true
            web.loadUrl(url)
        }
    }

    private fun handle(url: String?): Boolean {
        if (url == null) return false
        if (inApp) return false
        if (url.startsWith("file:///android_asset/")) return false
        if (sameSite(url)) return false
        openExternal(url)
        return true
    }

    private inner class Client : WebViewClient() {
        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = handle(url)

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
            handle(request?.url?.toString())

        override fun onPageFinished(view: WebView?, url: String?) {
            if (sameSite(url)) inApp = false
        }

        override fun onReceivedError(
            view: WebView?, request: WebResourceRequest?, error: WebResourceError?
        ) {
            if (request != null && request.isForMainFrame) showOffline()
        }

        @Deprecated("Deprecated in Java")
        override fun onReceivedError(
            view: WebView?, errorCode: Int, description: String?, failingUrl: String?
        ) {
            if (failingUrl != null && failingUrl != OFFLINE_URL) showOffline()
        }
    }

    private fun showOffline() {
        inApp = false
        web.post { web.loadUrl(OFFLINE_URL) }
    }

    // Functions the website can call.
    private inner class Bridge {
        @JavascriptInterface
        fun open(url: String) {
            runOnUiThread { openExternal(url) }
        }

        @JavascriptInterface
        fun openInApp(url: String) {
            runOnUiThread {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    inApp = true
                    web.loadUrl(url)
                }
            }
        }

        @JavascriptInterface
        fun retry() {
            runOnUiThread { web.loadUrl(SITE_URL) }
        }

        @JavascriptInterface
        fun siteUrl(): String = SITE_URL
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (inApp && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
        pausedAt = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        if (pausedAt > 0 && !inApp && System.currentTimeMillis() - pausedAt > RELOAD_AFTER_MS) {
            web.reload()
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
