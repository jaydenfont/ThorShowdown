package com.example.thorshowdown.showdown

/** Kind of room announced by `|init|`. */
public enum class RoomType(
    internal val wireName: String,
) {
    BATTLE("battle"),
    CHAT("chat"),
    ;

    internal companion object {
        /** Returns the room type for [wireName], or null if it isn't a known type. */
        fun fromWireName(wireName: String): RoomType? = entries.firstOrNull { it.wireName == wireName }
    }
}
