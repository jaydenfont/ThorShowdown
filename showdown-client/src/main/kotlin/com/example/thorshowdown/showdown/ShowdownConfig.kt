package com.example.thorshowdown.showdown

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where and how the client connects. Defaults point at the public Showdown server. */
public data class ShowdownConfig(
    val socketUrl: String = DEFAULT_SOCKET_URL,
    val loginUrl: String = DEFAULT_LOGIN_URL,
    val userAgent: String = DEFAULT_USER_AGENT,
    /** Longest [ShowdownClient.login] waits overall, including waiting for the challstr. */
    val loginTimeout: Duration = DEFAULT_TIMEOUT,
) {
    public companion object {
        public const val DEFAULT_SOCKET_URL: String = "wss://sim3.psim.us/showdown/websocket"
        public const val DEFAULT_LOGIN_URL: String = "https://play.pokemonshowdown.com/api/login"
        public const val DEFAULT_USER_AGENT: String = "ThorShowdown"
        public val DEFAULT_TIMEOUT: Duration = 20.seconds
    }
}
