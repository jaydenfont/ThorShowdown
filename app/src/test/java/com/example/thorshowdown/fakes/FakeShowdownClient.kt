package com.example.thorshowdown.fakes

import com.example.thorshowdown.showdown.Choice
import com.example.thorshowdown.showdown.LoginResult
import com.example.thorshowdown.showdown.ShowdownClient
import com.example.thorshowdown.showdown.ShowdownEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException

/** Scriptable stand-in for [ShowdownClient]: records connects and logins, answers with [loginResult]. */
class FakeShowdownClient(
    var loginResult: LoginResult = LoginResult.Success(USERNAME, SID),
) : ShowdownClient {
    override val events: SharedFlow<ShowdownEvent> = MutableSharedFlow()

    /** When set, [connect] throws it, as if the server were unreachable. */
    var connectFailure: IOException? = null

    /** When set, [login] suspends until it completes, so tests can observe the in-progress state. */
    var loginGate: CompletableDeferred<Unit>? = null

    var connectCount = 0
        private set

    /** Arguments of each login call, in order. */
    val logins = mutableListOf<Login>()

    override suspend fun connect() {
        connectFailure?.let { throw it }
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

    override suspend fun leave(roomId: String) = Unit

    override suspend fun choose(
        roomId: String,
        choice: Choice,
        rqid: Int?,
    ) = Unit

    override suspend fun forfeit(roomId: String) = Unit

    data class Login(
        val name: String,
        val password: String,
    )

    companion object {
        const val USERNAME = "Alice"
        const val SID = "alice%2C1%2Csession"
    }
}
