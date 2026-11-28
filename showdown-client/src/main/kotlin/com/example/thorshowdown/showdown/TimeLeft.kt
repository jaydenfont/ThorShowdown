package com.example.thorshowdown.showdown

/** This player's battle-timer clock, from the `|inactive|` message `Time left: N sec this turn | M sec total`. */
public data class TimeLeft(
    val turnSeconds: Int,
    val totalSeconds: Int,
) {
    public companion object {
        private val TIME_LEFT_PATTERN = Regex("""^Time left: (\d+) sec this turn \| (\d+) sec total""")
        private const val TURN_GROUP = 1
        private const val TOTAL_GROUP = 2

        /** Null for any other timer message (announcements, warnings about the opponent). */
        public fun parse(message: String): TimeLeft? {
            val groups = TIME_LEFT_PATTERN.find(message.trim())?.groupValues ?: return null
            return TimeLeft(groups[TURN_GROUP].toInt(), groups[TOTAL_GROUP].toInt())
        }
    }
}
