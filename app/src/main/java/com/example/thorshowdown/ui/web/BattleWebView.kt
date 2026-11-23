package com.example.thorshowdown.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.util.logging.Logger

private val logger: Logger = Logger.getLogger("BattleWebView")

private const val CSS_ASSET = "webview/battle.css"
private const val SCRIPT_ASSET = "webview/battle.js"
private const val INSTALL_FUNCTION = "thorShowdownInstall"

/**
 * The Showdown web client showing battle [roomId] (or a blank page when null), view only.
 * One WebView lives as long as this composable; changing [roomId] loads that room's page.
 */
@Composable
fun BattleWebView(
    roomId: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webView = remember { createBattleWebView(context) }
    DisposableEffect(webView) { onDispose { webView.destroy() } }
    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = { it.show(ShowdownUrls.roomUrl(roomId)) },
    )
}

/** Loads [url] unless it's already the page being shown; tracked by us, since Showdown rewrites its own URL. */
private fun WebView.show(url: String) {
    if (tag == url) return
    tag = url
    logger.info { "Showing $url" }
    loadUrl(url)
}

// The Showdown web client is a JavaScript app; navigation is locked to it by BattleWebViewClient.
@SuppressLint("SetJavaScriptEnabled")
private fun createBattleWebView(context: Context): WebView {
    WebView.setWebContentsDebuggingEnabled(context.isDebuggable())
    val script = installScript(context)
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webChromeClient = SilentConsoleChromeClient()
        webViewClient = BattleWebViewClient(injectOnCommit = script.takeUnless { injectAtDocumentStart(this, it) })
    }
}

/**
 * Runs [script] before Showdown's own scripts on every web client page, so its controls never
 * show unstyled. Returns false when this WebView can't, and the caller injects on commit instead.
 * (The page's load event can come a minute late while ads load, so it can't be used.)
 */
private fun injectAtDocumentStart(
    webView: WebView,
    script: String,
): Boolean {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        logger.warning("Document-start scripts unsupported; injecting when each page is first drawn")
        return false
    }
    WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(ShowdownUrls.ORIGIN))
    return true
}

/** battle.js followed by a call that installs battle.css. */
private fun installScript(context: Context): String {
    val css = context.readAsset(CSS_ASSET)
    val script = context.readAsset(SCRIPT_ASSET)
    return "$script\n$INSTALL_FUNCTION(${JSONObject.quote(css)});"
}

private fun Context.readAsset(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

private fun Context.isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/**
 * Keeps the WebView on the Showdown web client. When document-start scripts aren't available,
 * [injectOnCommit] is run as soon as each page is first drawn.
 */
private class BattleWebViewClient(
    private val injectOnCommit: String?,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url.toString()
        val allowed = ShowdownUrls.isAllowed(url)
        if (!allowed) logger.warning { "Blocked navigation to $url" }
        return !allowed
    }

    override fun onPageCommitVisible(
        view: WebView,
        url: String,
    ) {
        logger.info { "Page visible: $url" }
        if (injectOnCommit != null && ShowdownUrls.isAllowed(url)) view.evaluateJavascript(injectOnCommit, null)
    }
}

/** Showdown logs the whole protocol (our team included) to the console; keep it out of logcat. */
private class SilentConsoleChromeClient : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
}
