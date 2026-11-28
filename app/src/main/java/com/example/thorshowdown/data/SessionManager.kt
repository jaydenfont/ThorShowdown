package com.example.thorshowdown.data

import com.example.thorshowdown.showdown.BattleRequest
import com.example.thorshowdown.showdown.Choice
import com.example.thorshowdown.showdown.NotConnectedException
import com.example.thorshowdown.showdown.RoomType
import com.example.thorshowdown.showdown.ShowdownClient
import com.example.thorshowdown.showdown.ShowdownEvent
import com.example.thorshowdown.showdown.TimeLeft
import com.example.thorshowdown.showdown.toId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.logging.Logger

/**
 * Tracks one [BattleSession] per battle room and which room is current, and sends the
 * commands that belong to a room. Fed by [handle]; holds no UI objects.
 */
class SessionManager(
    private val client: ShowdownClient,
) {
    private val mutableState = MutableStateFlow(SessionsState())
    val state: StateFlow<SessionsState> = mutableState.asStateFlow()

    /** Applies one server event. Events that aren't about a known battle room are ignored. */
    fun handle(event: ShowdownEvent) {
        when (event) {
            is ShowdownEvent.RoomInit -> if (event.type == RoomType.BATTLE) startSession(event.roomId)
            is ShowdownEvent.RoomDeinit -> endSession(event.roomId)
            is ShowdownEvent.Title -> updateSession(event.roomId) { it.copy(title = event.title) }
            is ShowdownEvent.Request -> updateSession(event.roomId) { it.withRequest(event.request) }
            is ShowdownEvent.Turn -> updateSession(event.roomId) { it.copy(turn = event.number) }
            is ShowdownEvent.BattleError ->
                updateSession(event.roomId) { it.copy(lastError = event.message, waitingForOpponent = false) }
            is ShowdownEvent.Win -> updateSession(event.roomId) { it.copy(result = resultFor(it, event.winner)) }
            is ShowdownEvent.Tie -> updateSession(event.roomId) { it.copy(result = BattleResult.Tied) }
            is ShowdownEvent.Inactive -> updateSession(event.roomId) { it.withTimerMessage(event.message) }
            is ShowdownEvent.InactiveOff -> updateSession(event.roomId) { it.copy(timerOn = false, timeLeft = null) }
            else -> Unit
        }
    }

    /** Makes [roomId] current; it never leaves or forfeits anything. */
    fun selectRoom(roomId: String) {
        mutableState.update { state ->
            if (roomId in state.sessions) {
                state.copy(currentRoomId = roomId)
            } else {
                logger.warning { "Can't select unknown room $roomId" }
                state
            }
        }
    }

    /** Uses move [slot] (1-based) if the player has a choice to make in [roomId]. */
    suspend fun chooseMove(
        roomId: String,
        slot: Int,
        terastallize: Boolean,
    ) = choose(roomId, Choice.Move(slot, terastallize))

    /** Switches to party [slot] (1-based) if the player has a choice to make in [roomId]. */
    suspend fun switchTo(
        roomId: String,
        slot: Int,
    ) = choose(roomId, Choice.Switch(slot))

    /**
     * Takes back the choice sent for the current request, so the player can choose again.
     * If the turn already ran, the server answers `|error|`, which shows as [BattleSession.lastError].
     */
    suspend fun undo(roomId: String) {
        val session = mutableState.value.sessions[roomId]
        if (session == null || !session.canUndo) {
            logger.info { "Ignoring undo in $roomId: no choice to take back" }
            return
        }
        val sent = send(roomId) { client.choose(roomId, Choice.Undo, session.request?.rqid) }
        if (sent) updateSession(roomId) { it.copy(waitingForOpponent = false) }
    }

    /** Asks the server to turn the battle timer on; [BattleSession.timerOn] follows its `|inactive|`. */
    suspend fun startTimer(roomId: String) {
        send(roomId) { client.startTimer(roomId) }
    }

    suspend fun forfeit(roomId: String) {
        send(roomId) { client.forfeit(roomId) }
    }

    /** Leaves [roomId], forfeiting first if the battle isn't over. The session goes on `|deinit|`. */
    suspend fun leave(roomId: String) {
        val unfinished = mutableState.value.sessions[roomId]?.result == null
        if (unfinished && !send(roomId) { client.forfeit(roomId) }) return
        send(roomId) { client.leave(roomId) }
    }

    private suspend fun choose(
        roomId: String,
        choice: Choice,
    ) {
        val rqid = claimChoice(roomId)
        if (rqid == null) {
            logger.info { "Ignoring $choice in $roomId: no choice is awaited" }
            return
        }
        val sent = send(roomId) { client.choose(roomId, choice, rqid) }
        if (!sent) updateSession(roomId) { it.copy(waitingForOpponent = false) }
    }

    /**
     * Atomically marks the session as having chosen, so two quick taps can't both send.
     * Returns the request's rqid, or null if no choice is awaited.
     */
    private fun claimChoice(roomId: String): Int? {
        var rqid: Int? = null
        mutableState.update { state ->
            val session = state.sessions[roomId]
            if (session == null || !session.awaitingChoice) return@update state
            rqid = session.request?.rqid
            state.copy(sessions = state.sessions + (roomId to session.copy(waitingForOpponent = true)))
        }
        return rqid
    }

    /** Runs [command]; a dead connection is logged and recorded on the session. Returns whether it was sent. */
    private suspend fun send(
        roomId: String,
        command: suspend () -> Unit,
    ): Boolean =
        try {
            command()
            true
        } catch (e: IOException) {
            onSendFailed(roomId, e)
        } catch (e: NotConnectedException) {
            onSendFailed(roomId, e)
        }

    private fun onSendFailed(
        roomId: String,
        error: Exception,
    ): Boolean {
        logger.warning { "Command for $roomId failed: ${error.message}" }
        updateSession(roomId) { it.copy(lastError = SEND_FAILED_MESSAGE) }
        return false
    }

    private fun BattleSession.withRequest(request: BattleRequest): BattleSession =
        copy(request = request, waitingForOpponent = request.wait, lastError = null)

    /** Any `|inactive|` means the timer runs; only `Time left` readings (ours) move the countdown. */
    private fun BattleSession.withTimerMessage(message: String): BattleSession =
        copy(timerOn = true, timeLeft = TimeLeft.parse(message) ?: timeLeft)

    /** "Us" is the side named in our requests; Showdown compares names by ID. */
    private fun resultFor(
        session: BattleSession,
        winner: String,
    ): BattleResult {
        val ourName = session.request?.side?.name
        if (ourName == null) logger.warning { "${session.roomId} ended before any request; can't tell who won" }
        return if (ourName != null && toId(ourName) == toId(winner)) BattleResult.Won else BattleResult.Lost
    }

    private fun startSession(roomId: String) {
        logger.info { "Battle started: $roomId" }
        mutableState.update { SessionsState(it.sessions + (roomId to BattleSession(roomId)), currentRoomId = roomId) }
    }

    /** Removes [roomId]; if it was current, the most recently created remaining session takes over. */
    private fun endSession(roomId: String) {
        mutableState.update { state ->
            if (roomId !in state.sessions) return@update state
            val remaining = state.sessions - roomId
            val current = if (state.currentRoomId == roomId) remaining.keys.lastOrNull() else state.currentRoomId
            SessionsState(remaining, current)
        }
        logger.info { "Battle room closed: $roomId" }
    }

    private fun updateSession(
        roomId: String,
        change: (BattleSession) -> BattleSession,
    ) {
        mutableState.update { state ->
            val session = state.sessions[roomId]
            if (session == null) {
                logger.fine { "Ignoring event for unknown room $roomId" }
                return@update state
            }
            state.copy(sessions = state.sessions + (roomId to change(session)))
        }
    }

    private companion object {
        val logger: Logger = Logger.getLogger(SessionManager::class.java.name)
        const val SEND_FAILED_MESSAGE = "Not connected to Showdown; the command wasn't sent"
    }
}
