package com.example.thorshowdown.showdown.fakes

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/** Captures every record logged under [loggerName] (and its children) while [use] runs. */
class LogCapture(
    loggerName: String,
) {
    private val logger = Logger.getLogger(loggerName)
    private val records = mutableListOf<LogRecord>()

    /** Formatted messages of all captured records. */
    val messages: List<String> get() = records.map { it.message }

    fun <T> use(block: () -> T): T {
        val previousLevel = logger.level
        val handler = CollectingHandler()
        logger.level = Level.ALL
        logger.addHandler(handler)
        try {
            return block()
        } finally {
            logger.removeHandler(handler)
            logger.level = previousLevel
        }
    }

    private inner class CollectingHandler : Handler() {
        init {
            level = Level.ALL
        }

        override fun publish(record: LogRecord) {
            records += record
        }

        override fun flush() = Unit

        override fun close() = Unit
    }
}
