package com.example.thorshowdown.data.web

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.logging.Logger
import kotlin.coroutines.resume

/**
 * Signs the WebView in by giving it the `sid` cookie that `/api/login` set for the native client.
 * The cookie is shared across `pokemonshowdown.com` (the web client asks the login server with it)
 * and HTTPS only. `CookieManager` persists it like any browser cookie.
 */
class CookieManagerWebSession(
    private val cookies: CookieManager = CookieManager.getInstance(),
) : WebSession {
    /** Runs on the main thread: `CookieManager` calls back through the caller's Looper. */
    override suspend fun signIn(sid: String) =
        withContext(Dispatchers.Main) {
            val accepted = setSidCookie(sid)
            if (!accepted) logger.warning("The WebView refused the sid cookie; the top screen will be a spectator")
            cookies.flush()
        }

    private suspend fun setSidCookie(sid: String): Boolean =
        suspendCancellableCoroutine { done ->
            cookies.setCookie(COOKIE_URL, sidCookie(sid)) { accepted -> done.resume(accepted) }
        }

    private fun sidCookie(sid: String): String = "$SID_NAME=$sid; $DOMAIN_ATTRIBUTE; $PATH_ATTRIBUTE; $SECURE_ATTRIBUTE"

    private companion object {
        val logger: Logger = Logger.getLogger(CookieManagerWebSession::class.java.name)
        const val COOKIE_URL = "https://pokemonshowdown.com"
        const val SID_NAME = "sid"
        const val DOMAIN_ATTRIBUTE = "Domain=.pokemonshowdown.com"
        const val PATH_ATTRIBUTE = "Path=/"
        const val SECURE_ATTRIBUTE = "Secure"
    }
}
