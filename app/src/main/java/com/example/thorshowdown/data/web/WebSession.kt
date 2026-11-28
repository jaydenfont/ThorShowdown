package com.example.thorshowdown.data.web

/** Signs the top-screen WebView in as the native user, so it shows our side's hidden info. */
interface WebSession {
    /** Hands the login's `sid` session cookie to the WebView. */
    suspend fun signIn(sid: String)
}
