package com.example.thorshowdown.showdown

import java.util.logging.Logger

/** Turns raw Showdown server messages into [ShowdownEvent]s. Pure; does no I/O. */
public object ProtocolParser {
    private val logger = Logger.getLogger(ProtocolParser::class.java.name)
    private val TAG_PATTERN = Regex("""^\[([a-z-]+)]\s?(.*)$""")

    /** Message types whose last field may itself contain the field separator. */
    private val BOUNDED_FIELD_COUNTS =
        mapOf(
            Protocol.MessageType.CHALLSTR to 1,
            Protocol.MessageType.UPDATE_USER to 4,
            Protocol.MessageType.NAME_TAKEN to 2,
            Protocol.MessageType.POPUP to 1,
            Protocol.MessageType.PRIVATE_MESSAGE to 3,
            Protocol.MessageType.TITLE to 1,
            Protocol.MessageType.WIN to 1,
            Protocol.MessageType.ERROR to 1,
            Protocol.MessageType.INACTIVE to 1,
            Protocol.MessageType.INACTIVE_OFF to 1,
            Protocol.MessageType.UPDATE_SEARCH to 1,
            Protocol.MessageType.REQUEST to 1,
        )

    /** Parses one raw WebSocket text message, which may contain several rooms' lines. */
    public fun parse(raw: String): List<ShowdownEvent> {
        var roomId = GLOBAL_ROOM_ID
        val events = mutableListOf<ShowdownEvent>()
        for (rawLine in raw.split(Protocol.LINE_SEPARATOR)) {
            val line = rawLine.removeSuffix(Protocol.CARRIAGE_RETURN)
            if (line.isEmpty()) continue
            if (line.startsWith(Protocol.ROOM_MARKER)) {
                roomId = line.removePrefix(Protocol.ROOM_MARKER)
                continue
            }
            parseLine(roomId, line)?.let(events::add)
        }
        return events
    }

    private fun parseLine(
        roomId: String,
        line: String,
    ): ShowdownEvent? {
        val body = line.removePrefix(Protocol.FIELD_SEPARATOR)
        val type = body.substringBefore(Protocol.FIELD_SEPARATOR)
        val fields = splitArgs(type, body.substringAfter(Protocol.FIELD_SEPARATOR, missingDelimiterValue = ""))
        return typed(roomId, type, fields) ?: unparsed(roomId, type, fields)
    }

    /** Returns the typed event for [type], or null if the type is untyped or its fields are malformed. */
    private fun typed(
        roomId: String,
        type: String,
        fields: List<String>,
    ): ShowdownEvent? {
        val event =
            when (type) {
                Protocol.MessageType.CHALLSTR -> ShowdownEvent.ChallStr(fields.first())
                Protocol.MessageType.TURN -> fields.first().toIntOrNull()?.let { ShowdownEvent.Turn(roomId, it) }
                Protocol.MessageType.UPDATE_USER -> updateUser(fields)
                Protocol.MessageType.NAME_TAKEN -> fields.takeIf { it.size == 2 }?.let { ShowdownEvent.NameTaken(it[0], it[1]) }
                Protocol.MessageType.POPUP -> ShowdownEvent.Popup(fields.first())
                Protocol.MessageType.PRIVATE_MESSAGE -> privateMessage(fields)
                Protocol.MessageType.INIT -> RoomType.fromWireName(fields.first())?.let { ShowdownEvent.RoomInit(roomId, it) }
                Protocol.MessageType.TITLE -> ShowdownEvent.Title(roomId, fields.first())
                Protocol.MessageType.DEINIT -> ShowdownEvent.RoomDeinit(roomId)
                Protocol.MessageType.WIN -> ShowdownEvent.Win(roomId, fields.first())
                Protocol.MessageType.TIE -> ShowdownEvent.Tie(roomId)
                Protocol.MessageType.ERROR -> ShowdownEvent.BattleError(roomId, fields.first())
                Protocol.MessageType.INACTIVE -> ShowdownEvent.Inactive(roomId, fields.first())
                Protocol.MessageType.INACTIVE_OFF -> ShowdownEvent.InactiveOff(roomId, fields.first())
                Protocol.MessageType.UPDATE_SEARCH -> updateSearch(fields.first())
                Protocol.MessageType.REQUEST -> request(roomId, fields.first())
                else -> return null
            }
        if (event == null) logger.warning("Malformed |$type| line in room '$roomId'; keeping it as Unparsed")
        return event
    }

    private fun updateUser(fields: List<String>): ShowdownEvent.UpdateUser? {
        if (fields.size < 3) return null
        val (name, named, avatar) = fields
        return ShowdownEvent.UpdateUser(withoutRank(name), named == Protocol.TRUE_FLAG, avatar)
    }

    private fun updateSearch(json: String): ShowdownEvent.UpdateSearch? =
        ProtocolJson.decodeOrNull(ProtocolJson.UpdateSearchPayload.serializer(), json)?.let {
            ShowdownEvent.UpdateSearch(it.searching, it.games.orEmpty())
        }

    private fun request(
        roomId: String,
        json: String,
    ): ShowdownEvent.Request? =
        ProtocolJson.decodeOrNull(BattleRequest.serializer(), json)?.let {
            ShowdownEvent.Request(roomId, it, json)
        }

    private fun privateMessage(fields: List<String>): ShowdownEvent.PrivateMessage? {
        if (fields.size < 3) return null
        val (from, to, message) = fields
        return ShowdownEvent.PrivateMessage(withoutRank(from), withoutRank(to), message)
    }

    /**
     * Usernames arrive prefixed with a rank symbol (a space for regular users).
     * A lone symbol is a name in its own right: `~` is the server itself.
     */
    private fun withoutRank(name: String): String {
        val hasRankPrefix = name.length > 1 && !name.first().isLetterOrDigit()
        return if (hasRankPrefix) name.drop(1) else name
    }

    private fun splitArgs(
        type: String,
        argText: String,
    ): List<String> {
        val limit = BOUNDED_FIELD_COUNTS[type] ?: 0
        return argText.split(Protocol.FIELD_SEPARATOR, limit = limit)
    }

    private fun unparsed(
        roomId: String,
        type: String,
        fields: List<String>,
    ): ShowdownEvent.Unparsed {
        val args = mutableListOf<String>()
        val tags = linkedMapOf<String, String>()
        for (field in fields) {
            val tag = TAG_PATTERN.matchEntire(field)
            if (tag == null) {
                args += field
            } else {
                tags[tag.groupValues[1]] = tag.groupValues[2]
            }
        }
        return ShowdownEvent.Unparsed(roomId, type, args, tags)
    }
}
