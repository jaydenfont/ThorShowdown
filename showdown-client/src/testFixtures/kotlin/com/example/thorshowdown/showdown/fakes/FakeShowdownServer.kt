package com.example.thorshowdown.showdown.fakes

import com.example.thorshowdown.showdown.Transport
import com.example.thorshowdown.showdown.TransportEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long [FakeShowdownServer.awaitSent] waits by default; generous for a slow emulator. */
private val DEFAULT_AWAIT_TIMEOUT = 5.seconds

/**
 * In-memory stand-in for the Showdown WebSocket. Records what the client sends and lets
 * tests push server messages. Like the real server, it refuses messages without `room|` framing.
 * Safe to use from several threads: on a device the app sends from its own dispatchers.
 */
class FakeShowdownServer : Transport {
    private val toClient = Channel<TransportEvent>(Channel.UNLIMITED)
    private val sentMessages = MutableStateFlow<List<String>>(emptyList())

    /** Name to accept on `/trn` when [autoLogin] is on, else null. */
    @Volatile private var autoLoginName: String? = null

    /** Messages before this index were already matched by [awaitSent]. */
    @Volatile private var awaitedCount = 0

    /** Every message the client sent, in order. */
    val sent: List<String> get() = sentMessages.value

    /** URL the client opened, or null if it never connected. */
    @Volatile var openedUrl: String? = null
        private set

    /** Number of times the client opened the socket. */
    @Volatile var openCount = 0
        private set

    /** Optional scripted reply: given a message from the client, returns a raw server message to push back. */
    @Volatile var replyTo: (String) -> String? = { null }

    /** When true, every send fails as if the socket had just died. */
    @Volatile var failSends = false

    override val incoming: Flow<TransportEvent> = toClient.receiveAsFlow()

    /**
     * Answers the login handshake like the real server: a challstr on every open, and
     * `|updateuser|` naming [username] when the client claims a name with `/trn`.
     */
    fun autoLogin(username: String) {
        autoLoginName = username
    }

    /**
     * Waits until the client sends [command] (the part after `room|`, compared as a prefix so the
     * trailing `|rqid` doesn't matter) and returns the whole message. Each call matches a later message
     * than the previous call did, so a command sent twice is awaited twice.
     * Throws [AssertionError] listing everything sent if nothing matches within [timeout].
     */
    suspend fun awaitSent(
        command: String,
        timeout: Duration = DEFAULT_AWAIT_TIMEOUT,
    ): String {
        val from = awaitedCount
        val match =
            withTimeoutOrNull(timeout) {
                sentMessages
                    .map { messages -> messages.withIndex().drop(from).firstOrNull { matches(it.value, command) } }
                    .filterNotNull()
                    .first()
            } ?: throw AssertionError("Client never sent '$command' (sent: $sent)")
        awaitedCount = match.index + 1
        return match.value
    }

    override suspend fun open(url: String) {
        openedUrl = url
        openCount++
        if (autoLoginName != null) push(CHALLSTR_MESSAGE)
    }

    override suspend fun send(text: String) {
        if (failSends) throw IOException(SOCKET_CLOSED)
        if (FRAME_SEPARATOR !in text) throw AssertionError("Showdown drops messages without room| framing: '$text'")
        sentMessages.update { it + text }
        loginReplyTo(text)?.let(::push)
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

    private fun matches(
        message: String,
        command: String,
    ): Boolean = message.substringAfter(FRAME_SEPARATOR).startsWith(command)

    private fun loginReplyTo(text: String): String? =
        autoLoginName?.takeIf { text.startsWith(TRN_PREFIX) }?.let { "$UPDATE_USER_PREFIX$it$UPDATE_USER_NAMED_SUFFIX" }

    private companion object {
        const val FRAME_SEPARATOR = "|"
        const val SOCKET_CLOSED = "socket closed"
        const val CHALLSTR_MESSAGE = "|challstr|4|fake-challstr"
        const val TRN_PREFIX = "|/trn "
        const val UPDATE_USER_PREFIX = "|updateuser| "
        const val UPDATE_USER_NAMED_SUFFIX = "|1|170|{}"
    }
}
