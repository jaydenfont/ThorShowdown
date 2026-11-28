package com.example.thorshowdown.ui.web

import java.net.URI
import java.net.URISyntaxException

/** Showdown web client URLs, and which pages the top-screen WebView may open. */
object ShowdownUrls {
    private const val SCHEME = "https"
    private const val HOST = "play.pokemonshowdown.com"

    /** The web client's origin: the only one the WebView may open or inject into. */
    const val ORIGIN = "$SCHEME://$HOST"
    private const val WEB_CLIENT = "$ORIGIN/"

    /** Shown when there's no battle; loading it also drops the page's Showdown connection. */
    const val BLANK = "about:blank"

    /** The web client page for [roomId], or [BLANK] when there's no room. */
    fun roomUrl(roomId: String?): String = roomId?.let { WEB_CLIENT + it } ?: BLANK

    /** Only the web client itself, over HTTPS; everything else (other sites, look-alikes) is blocked. */
    fun isAllowed(url: String): Boolean =
        try {
            val uri = URI(url)
            uri.scheme == SCHEME && uri.host == HOST
        } catch (e: URISyntaxException) {
            false
        }
}
