package com.example.thorshowdown.showdown

/** Wire-level constants of the Showdown protocol. */
internal object Protocol {
    const val FIELD_SEPARATOR = "|"
    const val LINE_SEPARATOR = "\n"
    const val CARRIAGE_RETURN = "\r"
    const val ROOM_MARKER = ">"
    const val TRUE_FLAG = "1"

    /** Separates a command from its arguments, and the words of a choice. */
    const val ARG_SEPARATOR = " "

    /** Outbound slash commands. */
    object Command {
        const val TRN = "/trn"
        const val USE_TEAM = "/utm"
        const val SEARCH = "/search"
        const val CANCEL_SEARCH = "/cancelsearch"
        const val JOIN = "/join"
        const val LEAVE = "/leave"
        const val CHOOSE = "/choose"
        const val FORFEIT = "/forfeit"
    }

    /** Words used inside a `/choose` command. */
    object Choice {
        const val MOVE = "move"
        const val SWITCH = "switch"
        const val TERASTALLIZE = "terastallize"
        const val DEFAULT = "default"
        const val UNDO = "undo"
    }

    /** Message types the parser turns into typed events. */
    object MessageType {
        const val CHALLSTR = "challstr"
        const val TURN = "turn"
        const val UPDATE_USER = "updateuser"
        const val NAME_TAKEN = "nametaken"
        const val POPUP = "popup"
        const val PRIVATE_MESSAGE = "pm"
        const val INIT = "init"
        const val TITLE = "title"
        const val DEINIT = "deinit"
        const val WIN = "win"
        const val TIE = "tie"
        const val ERROR = "error"
        const val INACTIVE = "inactive"
        const val INACTIVE_OFF = "inactiveoff"
        const val UPDATE_SEARCH = "updatesearch"
        const val REQUEST = "request"
    }
}
