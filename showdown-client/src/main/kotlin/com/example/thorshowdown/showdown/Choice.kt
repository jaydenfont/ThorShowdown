package com.example.thorshowdown.showdown

/** A decision in a singles battle. Slots are 1-based, as in the request. */
public sealed interface Choice {
    /** Use the move in [slot] (1-4), optionally Terastallizing first. */
    public data class Move(
        val slot: Int,
        val terastallize: Boolean = false,
    ) : Choice

    /** Switch to the Pokémon at [slot] in the request's team order. */
    public data class Switch(
        val slot: Int,
    ) : Choice

    /** Let the server choose. */
    public data object Default : Choice

    /** Cancel the choice already sent for this request. */
    public data object Undo : Choice
}

/** Protocol text for this choice, e.g. `move 1 terastallize`. */
internal fun Choice.toProtocol(): String =
    when (this) {
        is Choice.Move -> moveText(this)
        is Choice.Switch -> "${Protocol.Choice.SWITCH}${Protocol.ARG_SEPARATOR}$slot"
        Choice.Default -> Protocol.Choice.DEFAULT
        Choice.Undo -> Protocol.Choice.UNDO
    }

private fun moveText(move: Choice.Move): String {
    val parts = mutableListOf(Protocol.Choice.MOVE, move.slot.toString())
    if (move.terastallize) parts += Protocol.Choice.TERASTALLIZE
    return parts.joinToString(Protocol.ARG_SEPARATOR)
}
