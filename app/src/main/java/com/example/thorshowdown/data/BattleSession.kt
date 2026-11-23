package com.example.thorshowdown.data

import com.example.thorshowdown.showdown.BattleRequest

/** State of one battle room the user is playing in. */
data class BattleSession(
    val roomId: String,
    /** From `|title|`, e.g. "Alice vs. Bob"; null until it arrives. */
    val title: String? = null,
    /** The latest `|request|`; null before the first one. */
    val request: BattleRequest? = null,
    val turn: Int = 0,
    /** True after we chose, or while the server says there's nothing for us to decide. */
    val waitingForOpponent: Boolean = false,
    /** Why the last choice or command failed; cleared by the next request. */
    val lastError: String? = null,
    /** Set once the battle has ended. The session stays until the player leaves. */
    val result: BattleResult? = null,
) {
    /** Whether the player has a choice to make right now. */
    val awaitingChoice: Boolean
        get() = request != null && !request.wait && !waitingForOpponent && result == null

    /** Whether a choice was sent for the current request and can still be taken back. */
    val canUndo: Boolean
        get() = request != null && !request.wait && waitingForOpponent && result == null
}

/** How a battle ended, from this player's point of view. */
enum class BattleResult {
    Won,
    Lost,
    Tied,
}

/** All battle sessions in creation order, plus the one being shown. */
data class SessionsState(
    val sessions: Map<String, BattleSession> = emptyMap(),
    val currentRoomId: String? = null,
)
