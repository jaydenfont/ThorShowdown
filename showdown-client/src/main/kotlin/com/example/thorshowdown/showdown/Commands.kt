package com.example.thorshowdown.showdown

/** Builds outbound protocol messages. Every message is `ROOMID|COMMAND`; global commands use an empty room. */
internal object Commands {
    private const val NO_TEAM = "null"
    private const val TRN_REGISTERED_FLAG = "0"
    private const val TRN_ARG_SEPARATOR = ","
    private const val REDACTED = "<redacted>"
    private val TRN_ASSERTION = Regex("""^(\|${Protocol.Command.TRN} [^,]*,[^,]*,).*$""")

    /** Claims [name] on this connection using a login [assertion]. */
    fun trn(
        name: String,
        assertion: String,
    ): String = global(command(Protocol.Command.TRN, listOf(name, TRN_REGISTERED_FLAG, assertion).joinToString(TRN_ARG_SEPARATOR)))

    /** [message] made safe to log: the login assertion in `/trn` is replaced. */
    fun redactedForLog(message: String): String = message.replace(TRN_ASSERTION, "$1$REDACTED")

    fun useTeam(packedTeam: String?): String = global(command(Protocol.Command.USE_TEAM, packedTeam ?: NO_TEAM))

    fun search(formatId: String): String = global(command(Protocol.Command.SEARCH, formatId))

    fun cancelSearch(): String = global(Protocol.Command.CANCEL_SEARCH)

    fun join(roomId: String): String = global(command(Protocol.Command.JOIN, roomId))

    fun leave(roomId: String): String = global(command(Protocol.Command.LEAVE, roomId))

    /** The rqid suffix ties the choice to one request, so a late choice can't apply to a later turn. */
    fun choose(
        roomId: String,
        choice: Choice,
        rqid: Int?,
    ): String {
        val command = command(Protocol.Command.CHOOSE, choice.toProtocol())
        val withRqid = rqid?.let { command + Protocol.FIELD_SEPARATOR + it } ?: command
        return inRoom(roomId, withRqid)
    }

    fun forfeit(roomId: String): String = inRoom(roomId, Protocol.Command.FORFEIT)

    fun startTimer(roomId: String): String = inRoom(roomId, command(Protocol.Command.TIMER, Protocol.TIMER_ON))

    private fun command(
        name: String,
        argument: String,
    ): String = name + Protocol.ARG_SEPARATOR + argument

    private fun global(command: String): String = inRoom(GLOBAL_ROOM_ID, command)

    private fun inRoom(
        roomId: String,
        command: String,
    ): String = roomId + Protocol.FIELD_SEPARATOR + command
}
