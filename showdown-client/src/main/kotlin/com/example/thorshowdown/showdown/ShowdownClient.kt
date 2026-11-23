package com.example.thorshowdown.showdown

import kotlinx.coroutines.flow.SharedFlow

/**
 * Pokémon Showdown protocol client.
 *
 * [events] is hot: events emitted while nobody collects are lost, so start collecting
 * before calling [connect].
 */
public interface ShowdownClient {
    /** Everything received from the server, plus connection changes, in order. */
    public val events: SharedFlow<ShowdownEvent>

    /** Opens the connection. Emits [ShowdownEvent.Connected]. Throws [java.io.IOException] if it can't connect. */
    public suspend fun connect()

    /** Closes the connection. Emits [ShowdownEvent.Disconnected]; later commands throw [NotConnectedException]. */
    public suspend fun disconnect()

    /**
     * Logs in as [name] on the current connection, waiting for its challstr if needed.
     * Never throws for login problems; they are reported as [LoginResult.Failure].
     */
    public suspend fun login(
        name: String,
        password: String,
    ): LoginResult

    /** Sets the team for the next search or challenge. Null means no team (random formats). */
    public suspend fun useTeam(packedTeam: String?)

    /** Starts searching the ladder for [formatId]. Results arrive as [ShowdownEvent.UpdateSearch]. */
    public suspend fun search(formatId: String)

    /** Stops all ladder searches. */
    public suspend fun cancelSearch()

    /** Joins [roomId], e.g. to rejoin a battle. */
    public suspend fun join(roomId: String)

    /** Leaves [roomId]. */
    public suspend fun leave(roomId: String)

    /** Answers the request identified by [rqid] in battle [roomId]. Pass null if the request had no rqid. */
    public suspend fun choose(
        roomId: String,
        choice: Choice,
        rqid: Int?,
    )

    /** Forfeits the battle in [roomId]. */
    public suspend fun forfeit(roomId: String)

    /** Turns on the battle timer in [roomId]; the server confirms with [ShowdownEvent.Inactive]. */
    public suspend fun startTimer(roomId: String)
}
