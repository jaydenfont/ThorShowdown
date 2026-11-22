package com.example.thorshowdown.showdown

/** Room ID used for events that don't belong to a room. */
public const val GLOBAL_ROOM_ID: String = ""

/** A typed message received from the Showdown server. */
public sealed interface ShowdownEvent {
    /** Room the event belongs to, or [GLOBAL_ROOM_ID]. */
    public val roomId: String

    /** The connection to the server is open. */
    public data object Connected : ShowdownEvent {
        override val roomId: String = GLOBAL_ROOM_ID
    }

    /** The connection closed, by [ShowdownClient.disconnect] or by the server ([reason] if known). */
    public data class Disconnected(
        val reason: String?,
    ) : ShowdownEvent {
        override val roomId: String = GLOBAL_ROOM_ID
    }

    /** Challenge string needed to log in on this connection. */
    public data class ChallStr(
        val challstr: String,
    ) : ShowdownEvent {
        override val roomId: String = GLOBAL_ROOM_ID
    }

    /** The server's view of who this connection is. [named] is true once logged in as [name]. */
    public data class UpdateUser(
        val name: String,
        val named: Boolean,
        val avatar: String,
    ) : ShowdownEvent {
        override val roomId: String = GLOBAL_ROOM_ID
    }

    /** Login was refused because [name] is taken or reserved. */
    public data class NameTaken(
        val name: String,
        val message: String,
    ) : ShowdownEvent {
        override val roomId: String = GLOBAL_ROOM_ID
    }

    /** A message the server wants shown to the user. `||` in [message] marks a line break. */
    public data class Popup(
        val message: String,
    ) : ShowdownEvent {
        override val roomId: String = GLOBAL_ROOM_ID
    }

    /** A private message. Replies to global commands, including errors, come from [SYSTEM_SENDER]. */
    public data class PrivateMessage(
        val from: String,
        val to: String,
        val message: String,
    ) : ShowdownEvent {
        override val roomId: String = GLOBAL_ROOM_ID

        public companion object {
            /** Sender name the server uses for its own replies. */
            public const val SYSTEM_SENDER: String = "~"
        }
    }

    /**
     * Matchmaking status. [searching] lists format IDs being searched;
     * [games] maps battle room IDs to their titles.
     */
    public data class UpdateSearch(
        val searching: List<String>,
        val games: Map<String, String>,
    ) : ShowdownEvent {
        override val roomId: String = GLOBAL_ROOM_ID
    }

    /** This connection joined [roomId]. A battle room means a battle has started. */
    public data class RoomInit(
        override val roomId: String,
        val type: RoomType,
    ) : ShowdownEvent

    /** Display title of [roomId], e.g. "Alice vs. Bob". */
    public data class Title(
        override val roomId: String,
        val title: String,
    ) : ShowdownEvent

    /** This connection left [roomId]. */
    public data class RoomDeinit(
        override val roomId: String,
    ) : ShowdownEvent

    /** The server wants a decision in [roomId]. [rawJson] holds fields [request] doesn't model. */
    public data class Request(
        override val roomId: String,
        val request: BattleRequest,
        val rawJson: String,
    ) : ShowdownEvent

    /** The battle in [roomId] ended and [winner] won. */
    public data class Win(
        override val roomId: String,
        val winner: String,
    ) : ShowdownEvent

    /** The battle in [roomId] ended in a tie. */
    public data class Tie(
        override val roomId: String,
    ) : ShowdownEvent

    /** The server rejected something in [roomId], e.g. an invalid choice. */
    public data class BattleError(
        override val roomId: String,
        val message: String,
    ) : ShowdownEvent

    /** The battle timer is running; [message] says how long is left. */
    public data class Inactive(
        override val roomId: String,
        val message: String,
    ) : ShowdownEvent

    /** The battle timer was turned off. */
    public data class InactiveOff(
        override val roomId: String,
        val message: String,
    ) : ShowdownEvent

    /** Any message without a typed event. Kept so protocol changes stay visible. */
    public data class Unparsed(
        override val roomId: String,
        val type: String,
        val args: List<String>,
        val tags: Map<String, String>,
    ) : ShowdownEvent

    /** A new battle turn has started. */
    public data class Turn(
        override val roomId: String,
        val number: Int,
    ) : ShowdownEvent
}
