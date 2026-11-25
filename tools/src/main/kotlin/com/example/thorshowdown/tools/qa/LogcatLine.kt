package com.example.thorshowdown.tools.qa

/** One line of `adb logcat -v threadtime`, kept with its [raw] text. */
data class LogcatLine(
    val raw: String,
    val pid: Int,
    val level: Char,
    val tag: String,
    val message: String,
) {
    /** The checkpoint this line marks, if it is a `QA_CHECKPOINT <n> <Name>` line. */
    fun checkpoint(): CheckpointMark? = CHECKPOINT.find(message)?.let { CheckpointMark(it.groupValues[1].toInt(), it.groupValues[2]) }

    companion object {
        /** `MM-dd HH:mm:ss.SSS  pid  tid L tag: message` (tags may be padded before the colon). */
        private val THREADTIME = Regex("""^\S+ \S+\s+(\d+)\s+\d+ ([VDIWEF]) (.*?)\s*: (.*)$""")
        private val CHECKPOINT = Regex("""^QA_CHECKPOINT (\d+) (\w+)$""")

        /** Parses [raw], or returns null for lines that aren't threadtime log entries. */
        fun parse(raw: String): LogcatLine? =
            THREADTIME.find(raw)?.let {
                val (pid, level, tag, message) = it.destructured
                LogcatLine(raw, pid.toInt(), level.single(), tag, message)
            }
    }
}

/** A checkpoint the scenario reached: its 1-based [index] in the run and its enum [name]. */
data class CheckpointMark(
    val index: Int,
    val name: String,
)
