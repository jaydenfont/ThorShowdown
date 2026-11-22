package com.example.thorshowdown.showdown

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.logging.Logger

/** [ShowdownClient] built from a [Transport] and a [LoginApi]. Background work runs in [scope]. */
public class DefaultShowdownClient(
    private val config: ShowdownConfig,
    private val transport: Transport,
    private val loginApi: LoginApi,
    private val scope: CoroutineScope,
) : ShowdownClient {
    private val mutableEvents = MutableSharedFlow<ShowdownEvent>(extraBufferCapacity = EVENT_BUFFER_SIZE)
    override val events: SharedFlow<ShowdownEvent> = mutableEvents.asSharedFlow()

    @Volatile private var connected = false

    /** Challstr of the current connection; a new one arrives with every connection. */
    private val latestChallstr = MutableStateFlow<String?>(null)

    override suspend fun connect() {
        if (connected) return
        logger.info { "Connecting to ${config.socketUrl}" }
        transport.open(config.socketUrl)
        connected = true
        mutableEvents.emit(ShowdownEvent.Connected)
        scope.launch { transport.incoming.first(::handleUntilClosed) }
    }

    override suspend fun disconnect() {
        connected = false
        transport.close()
    }

    override suspend fun login(
        name: String,
        password: String,
    ): LoginResult {
        if (!connected) return LoginResult.Failure(LoginFailure.NotConnected, NOT_CONNECTED_MESSAGE)
        logger.info { "Logging in as $name" }
        val result =
            withTimeoutOrNull(config.loginTimeout) { attemptLogin(name, password) }
                ?: LoginResult.Failure(LoginFailure.Timeout, TIMEOUT_MESSAGE)
        logger.info { "Login as $name: $result" }
        return result
    }

    private suspend fun attemptLogin(
        name: String,
        password: String,
    ): LoginResult {
        val challstr = latestChallstr.filterNotNull().first()
        val assertion =
            try {
                loginApi.fetchAssertion(name, password, challstr)
            } catch (e: IOException) {
                return LoginResult.Failure(LoginFailure.Network, e.message ?: NETWORK_MESSAGE)
            }
        if (isRefused(assertion)) return LoginResult.Failure(LoginFailure.WrongPassword, refusalMessage(assertion))
        return claimName(name, assertion)
    }

    /** Sends `/trn` and waits for the server to accept or reject the name. */
    private suspend fun claimName(
        name: String,
        assertion: String,
    ): LoginResult =
        try {
            events
                .onSubscription { send(Commands.trn(name, assertion)) }
                .mapNotNull { loginOutcome(it, name) }
                .first()
        } catch (e: IOException) {
            LoginResult.Failure(LoginFailure.Network, e.message ?: CONNECTION_CLOSED_MESSAGE)
        } catch (e: NotConnectedException) {
            LoginResult.Failure(LoginFailure.Network, CONNECTION_CLOSED_MESSAGE)
        }

    /** The login's outcome if [event] decides it, else null. */
    private fun loginOutcome(
        event: ShowdownEvent,
        name: String,
    ): LoginResult? =
        when (event) {
            is ShowdownEvent.UpdateUser -> event.takeIf { it.named && toId(it.name) == toId(name) }?.let { LoginResult.Success(it.name) }
            is ShowdownEvent.NameTaken -> LoginResult.Failure(LoginFailure.NameTaken, event.message)
            is ShowdownEvent.Disconnected -> LoginResult.Failure(LoginFailure.Network, event.reason ?: CONNECTION_CLOSED_MESSAGE)
            else -> null
        }

    /** The login server signals refusal with an empty assertion or one starting with `;;`. */
    private fun isRefused(assertion: String): Boolean = assertion.isBlank() || assertion.startsWith(REFUSED_ASSERTION_PREFIX)

    private fun refusalMessage(assertion: String): String =
        assertion.removePrefix(REFUSED_ASSERTION_PREFIX).ifBlank { WRONG_PASSWORD_MESSAGE }

    override suspend fun useTeam(packedTeam: String?): Unit = send(Commands.useTeam(packedTeam))

    override suspend fun search(formatId: String): Unit = send(Commands.search(formatId))

    override suspend fun cancelSearch(): Unit = send(Commands.cancelSearch())

    override suspend fun join(roomId: String): Unit = send(Commands.join(roomId))

    override suspend fun leave(roomId: String): Unit = send(Commands.leave(roomId))

    override suspend fun choose(
        roomId: String,
        choice: Choice,
        rqid: Int?,
    ): Unit = send(Commands.choose(roomId, choice, rqid))

    override suspend fun forfeit(roomId: String): Unit = send(Commands.forfeit(roomId))

    private suspend fun send(message: String) {
        if (!connected) throw NotConnectedException()
        logger.fine { "Sending ${Commands.redactedForLog(message)}" }
        transport.send(message)
    }

    private suspend fun publish(event: ShowdownEvent) {
        if (event is ShowdownEvent.ChallStr) latestChallstr.value = event.challstr
        mutableEvents.emit(event)
    }

    /** Handles one transport event. Returns true once the connection has closed, ending collection. */
    private suspend fun handleUntilClosed(event: TransportEvent): Boolean =
        when (event) {
            is TransportEvent.Message -> {
                logger.fine { "Received ${event.text}" }
                ProtocolParser.parse(event.text).forEach { publish(it) }
                false
            }
            is TransportEvent.Closed -> {
                logger.info { "Disconnected: ${event.reason ?: CLOSED_BY_CLIENT_MESSAGE}" }
                connected = false
                latestChallstr.value = null
                mutableEvents.emit(ShowdownEvent.Disconnected(event.reason))
                true
            }
        }

    private companion object {
        val logger: Logger = Logger.getLogger(DefaultShowdownClient::class.java.name)
        const val EVENT_BUFFER_SIZE = 1024
        const val REFUSED_ASSERTION_PREFIX = ";;"
        const val WRONG_PASSWORD_MESSAGE = "Login refused; check the password"
        const val NOT_CONNECTED_MESSAGE = "Not connected to the Showdown server"
        const val TIMEOUT_MESSAGE = "The server didn't confirm the login in time"
        const val NETWORK_MESSAGE = "Couldn't reach the login server"
        const val CONNECTION_CLOSED_MESSAGE = "The connection closed during login"
        const val CLOSED_BY_CLIENT_MESSAGE = "closed by client"
    }
}
