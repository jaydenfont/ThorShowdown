package com.example.thorshowdown.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = BattleWebViewClient(installScript(context))
    }
}

/** battle.js followed by a call that installs battle.css. */
private fun installScript(context: Context): String {
    val css = context.readAsset(CSS_ASSET)
    val script = context.readAsset(SCRIPT_ASSET)
    return "$script\n$INSTALL_FUNCTION(${JSONObject.quote(css)});"
}

private fun Context.readAsset(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

private fun Context.isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** Keeps the WebView on the Showdown web client and styles every page it finishes loading. */
private class BattleWebViewClient(
    private val installScript: String,
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

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        if (ShowdownUrls.isAllowed(url)) view.evaluateJavascript(installScript, null)
    }
}
