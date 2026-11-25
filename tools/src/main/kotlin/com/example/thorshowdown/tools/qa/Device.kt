package com.example.thorshowdown.tools.qa

import java.io.File

/**
 * What the QA runner needs from the emulator. [AdbDevice] does it with `adb`; tests use a fake.
 * Screens: "top" is the default display, "bottom" the secondary (the Thor's touch screen).
 */
interface Device {
    /** Gets the device ready for a scenario: secondary display present, old acks gone, logcat cleared. */
    fun prepare()

    /** Starts recording both screens into [scenarioDir]. */
    fun startRecording(scenarioDir: File): Recording

    /** Streams logcat (threadtime format) from now until [LogStream.stop]. */
    fun logcat(): LogStream

    /** Starts the instrumented test class [testClass] in capture mode. */
    fun instrument(testClass: String): RunningInstrument

    fun screenshotTop(to: File)

    fun screenshotBottom(to: File)

    /** Tells the waiting scenario that checkpoint [index] was captured. */
    fun ack(index: Int)
}

/** Both screens being recorded. */
fun interface Recording {
    /** Stops recording and returns the video files (null where a recording failed). */
    fun stop(): Videos
}

data class Videos(
    val top: File?,
    val bottom: File?,
)

interface LogStream {
    /** Lines as they arrive; ends after [stop]. */
    val lines: Sequence<String>

    fun stop()
}

/** An instrumented test that is running. */
fun interface RunningInstrument {
    /** Waits for the test to finish and returns `am instrument`'s output. */
    fun await(): String
}
