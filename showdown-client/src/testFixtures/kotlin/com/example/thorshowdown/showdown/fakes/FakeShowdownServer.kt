package com.example.thorshowdown.showdown.fakes

import com.example.thorshowdown.showdown.Transport
import com.example.thorshowdown.showdown.TransportEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.IOException

/**
 * In-memory stand-in for the Showdown WebSocket. Records what the client sends and lets
 * tests push server messages. Like the real server, it refuses messages without `room|` framing.
 */
class FakeShowdownServer : Transport {
    private val toClient = Channel<TransportEvent>(Channel.UNLIMITED)

    /** Every message the client sent, in order. */
    val sent = mutableListOf<String>()

    /** URL the client opened, or null if it never connected. */
    var openedUrl: String? = null
        private set

    override val incoming: Flow<TransportEvent> = toClient.receiveAsFlow()

    override suspend fun open(url: String) {
        openedUrl = url
        openCount++
    }

    /** Optional scripted reply: given a message from the client, returns a raw server message to push back. */
    var replyTo: (String) -> String? = { null }

    /** When true, every send fails as if the socket had just died. */
    var failSends = false

    /** Number of times the client opened the socket. */
    var openCount = 0
        private set

    override suspend fun send(text: String) {
        if (failSends) throw IOException("socket closed")
        if (FRAME_SEPARATOR !in text) throw AssertionError("Showdown drops messages without room| framing: '$text'")
        sent += text
        replyTo(text)?.let(::push)
    }

    override suspend fun close() {
        toClient.trySend(TransportEvent.Closed(reason = null))
    }

    /** Delivers one raw WebSocket text message to the client. */
    fun push(raw: String) {
        toClient.trySend(TransportEvent.Message(raw))
    }

    /** Simulates the server dropping the connection. */
    fun drop(reason: String) {
        toClient.trySend(TransportEvent.Closed(reason))
    }

    private companion object {
        const val FRAME_SEPARATOR = "|"
    }
}
