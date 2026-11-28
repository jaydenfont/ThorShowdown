package com.example.thorshowdown.data.web

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume

/**
 * Runs against the app's real cookie store, so it clears all cookies afterwards
 * (the next login sets the sid again).
 */
@RunWith(AndroidJUnit4::class)
class CookieManagerWebSessionTest {
    private val cookies = CookieManager.getInstance()
    private val webSession = CookieManagerWebSession(cookies)

    @After
    fun clearCookies() =
        runBlocking {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { done -> cookies.removeAllCookies { done.resume(Unit) } }
            }
        }

    @Test
    fun CookieManagerWebSession_SignIn_WebClientGetsSidCookie() =
        runBlocking {
            webSession.signIn(SID)

            val sent = cookies.getCookie(WEB_CLIENT).orEmpty()

            assertTrue("cookies were: $sent", sent.contains(SID_COOKIE))
        }

    @Test
    fun CookieManagerWebSession_SignIn_SidIsSharedAcrossShowdownSubdomains() =
        runBlocking {
            webSession.signIn(SID)

            val sent = cookies.getCookie(LOGIN_SERVER).orEmpty()

            assertTrue("cookies were: $sent", sent.contains(SID_COOKIE))
        }

    @Test
    fun CookieManagerWebSession_SignIn_SidIsNotSentOverPlainHttp() =
        runBlocking {
            webSession.signIn(SID)

            val sent = cookies.getCookie(WEB_CLIENT_HTTP).orEmpty()

            assertFalse("cookies were: $sent", sent.contains(SID_COOKIE))
        }

    private companion object {
        const val SID = "thortest%2C1%2Csession"
        const val SID_COOKIE = "sid=$SID"
        const val WEB_CLIENT = "https://play.pokemonshowdown.com/"
        const val WEB_CLIENT_HTTP = "http://play.pokemonshowdown.com/"
        const val LOGIN_SERVER = "https://pokemonshowdown.com/"
    }
}
