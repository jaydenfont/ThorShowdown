package com.example.thorshowdown.qa

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.logging.Logger

/**
 * Marks the moments the QA runner screenshots. Each checkpoint logs `QA_CHECKPOINT <n> <Name>`.
 * With the instrumentation argument `qaCapture=true` (set by `:tools:qaRun`) it then waits until
 * the runner has captured both screens and written `/data/local/tmp/qa/ack-<n>`; without it the
 * checkpoint only logs, so the scenario runs normally from Android Studio.
 */
class QaCheckpoints(
    private val compose: ComposeTestRule,
) {
    private val capturing = InstrumentationRegistry.getArguments().getString(CAPTURE_ARGUMENT) == CAPTURE_ON
    private var count = 0

    /** Waits for the UI to settle, then hands [checkpoint] to the runner. */
    fun checkpoint(checkpoint: Enum<*>) {
        compose.waitForIdle()
        count++
        logger.info("$MARKER $count ${checkpoint.name}")
        if (capturing) awaitAck(count)
    }

    private fun awaitAck(index: Int) {
        val deadline = System.currentTimeMillis() + ACK_TIMEOUT_MILLIS
        while (!ackExists(index)) {
            check(System.currentTimeMillis() < deadline) { "$NO_ACK $index" }
            Thread.sleep(ACK_POLL_MILLIS)
        }
    }

    /** The runner writes acks as the shell user, which the app can't read; shell commands can. */
    private fun ackExists(index: Int): Boolean = shell("$LS_COMMAND $ACK_DIR/$ACK_PREFIX$index").contains("$ACK_PREFIX$index")

    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
    }

    private companion object {
        val logger: Logger = Logger.getLogger("QaCheckpoint")
        const val MARKER = "QA_CHECKPOINT"
        const val CAPTURE_ARGUMENT = "qaCapture"
        const val CAPTURE_ON = "true"
        const val ACK_DIR = "/data/local/tmp/qa"
        const val ACK_PREFIX = "ack-"
        const val LS_COMMAND = "ls"
        const val NO_ACK = "QA runner never acknowledged checkpoint"
        const val ACK_TIMEOUT_MILLIS = 10_000L
        const val ACK_POLL_MILLIS = 100L
    }
}
