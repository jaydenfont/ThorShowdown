package com.example.thorshowdown.fakes

import com.example.thorshowdown.showdown.Choice
import com.example.thorshowdown.showdown.LoginResult
import com.example.thorshowdown.showdown.ShowdownClient
import com.example.thorshowdown.showdown.ShowdownEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException

/**
 * Scriptable stand-in for [ShowdownClient]: records connects, logins and room commands,
 * answers logins with [loginResult], and lets tests push events with [emit].
 */
class FakeShowdownClient(
    var loginResult: LoginResult = LoginResult.Success(USERNAME, SID),
) : ShowdownClient {
    private val mutableEvents = MutableSharedFlow<ShowdownEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<ShowdownEvent> = mutableEvents.asSharedFlow()

    /** When set, [connect] throws it, as if the server were unreachable. */
    var connectFailure: IOException? = null

    /** When set, room commands throw it, as if the socket had died. */
    var commandFailure: Exception? = null

    /** When set, [login] suspends until it completes, so tests can observe the in-progress state. */
    var loginGate: CompletableDeferred<Unit>? = null

    var connectCount = 0
        private set

    /** How many collectors [events] had when [connect] was last called. */
    var subscribersAtConnect = 0
        private set

    /** Arguments of each login call, in order. */
    val logins = mutableListOf<Login>()

    /** Room commands sent, in order. */
    val roomCommands = mutableListOf<RoomCommand>()

    /** Delivers [event] to collectors, as if the server had sent it. */
    suspend fun emit(event: ShowdownEvent) = mutableEvents.emit(event)

    override suspend fun connect() {
        connectFailure?.let { throw it }
        subscribersAtConnect = mutableEvents.subscriptionCount.value
        connectCount++
    }

    override suspend fun disconnect() = Unit

    override suspend fun login(
        name: String,
        password: String,
    ): LoginResult {
        logins += Login(name, password)
        loginGate?.await()
        return loginResult
    }

    override suspend fun useTeam(packedTeam: String?) = Unit

    override suspend fun search(formatId: String) = Unit

    override suspend fun cancelSearch() = Unit

    override suspend fun join(roomId: String) = Unit

    override suspend fun leave(roomId: String) = record(RoomCommand.Leave(roomId))

    override suspend fun choose(
        roomId: String,
        choice: Choice,
        rqid: Int?,
    ) = record(RoomCommand.Choose(roomId, choice, rqid))

    override suspend fun forfeit(roomId: String) = record(RoomCommand.Forfeit(roomId))

    private fun record(command: RoomCommand) {
        commandFailure?.let { throw it }
        roomCommands += command
    }

    data class Login(
        val name: String,
        val password: String,
    )

    sealed interface RoomCommand {
        data class Choose(
            val roomId: String,
            val choice: Choice,
            val rqid: Int?,
        ) : RoomCommand

        data class Forfeit(
            val roomId: String,
        ) : RoomCommand

        data class Leave(
            val roomId: String,
        ) : RoomCommand
    }

    companion object {
        const val USERNAME = "Alice"
        const val SID = "alice%2C1%2Csession"
        private const val EVENT_BUFFER = 64
    }
}
