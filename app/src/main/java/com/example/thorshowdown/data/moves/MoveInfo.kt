package com.example.thorshowdown.data.moves

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the app knows about a move, from the bundled Showdown data. [accuracy] is null for moves that never miss. */
@Serializable
data class MoveInfo(
    val name: String,
    val type: String,
    val category: String,
    val basePower: Int,
    val accuracy: Int?,
    val priority: Int,
    val description: String,
)

/** Looks up moves by Showdown move ID (e.g. `thunderbolt`). Empty until the data has loaded. */
interface MoveDex {
    val moves: StateFlow<Map<String, MoveInfo>>
}

/** Parses the bundled `assets/data/moves.json` written by `:tools:generateMoveData`. */
object MoveData {
    fun parse(json: String): Map<String, MoveInfo> = Json.decodeFromString(json)
}
