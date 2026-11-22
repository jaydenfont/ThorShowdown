package com.example.thorshowdown.showdown

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [Transport] over an OkHttp WebSocket.
 *
 * One instance serves one connection at a time. [incoming] is a single-consumer stream,
 * matching [DefaultShowdownClient], which is its only collector.
 */
public class OkHttpTransport(
    private val config: ShowdownConfig,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : Transport {
    private val events = Channel<TransportEvent>(Channel.UNLIMITED)

    @Volatile private var socket: WebSocket? = null

    override val incoming: Flow<TransportEvent> = events.receiveAsFlow()

    override suspend fun open(url: String): Unit =
        suspendCancellableCoroutine { continuation ->
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header(HEADER_USER_AGENT, config.userAgent)
                    .build()
            val webSocket = httpClient.newWebSocket(request, Listener(continuation))
            socket = webSocket
            continuation.invokeOnCancellation { webSocket.cancel() }
        }

    override suspend fun send(text: String) {
        val sent = socket?.send(text) ?: false
        if (!sent) throw IOException(SEND_FAILED_MESSAGE)
    }

    override suspend fun close() {
        socket?.close(NORMAL_CLOSURE, null)
    }

    /** Resumes [opening] once the socket opens (or fails to), then forwards traffic to [events]. */
    private inner class Listener(
        private val opening: CancellableContinuation<Unit>,
    ) : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            if (opening.isActive) opening.resume(Unit)
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            events.trySend(TransportEvent.Message(text))
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            events.trySend(TransportEvent.Closed(reason.ifBlank { null }))
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            if (opening.isActive) {
                opening.resumeWithException(IOException(t.message, t))
            } else {
                events.trySend(TransportEvent.Closed(t.message))
            }
        }
    }

    private companion object {
        const val HEADER_USER_AGENT = "User-Agent"
        const val NORMAL_CLOSURE = 1000
        const val SEND_FAILED_MESSAGE = "WebSocket is not open"
    }
}
