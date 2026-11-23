package com.example.thorshowdown.data

/** Ladder formats the app can search. P0 is Random Battle only, which needs no team. */
enum class BattleFormat(
    val id: String,
) {
    Gen9RandomBattle("gen9randombattle"),
}

/** Where a ladder search stands. The server's `|updatesearch|` is the source of truth. */
sealed interface SearchState {
    /** Not searching. [error] says why the last search ended, if it failed. */
    data class Idle(
        val error: SearchError? = null,
    ) : SearchState

    /** The search was sent and the server hasn't confirmed it yet. */
    data class Requested(
        val format: BattleFormat,
    ) : SearchState

    /** The server is looking for an opponent. */
    data class Searching(
        val format: BattleFormat,
    ) : SearchState
}

/** Why a search ended without a battle. */
sealed interface SearchError {
    /** The server refused it; [message] is the server's own text. */
    data class Refused(
        val message: String,
    ) : SearchError

    /** The server stopped a confirmed search, e.g. the matched opponent went offline; [message] is its text. */
    data class Interrupted(
        val message: String,
    ) : SearchError

    /** The server didn't answer in time. */
    data object NoResponse : SearchError

    /** The command couldn't be sent. */
    data object SendFailed : SearchError
}
