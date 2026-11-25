package com.example.thorshowdown.tools.qa

import java.io.File
import java.io.IOException

/** Scripted [Device]: replays [logcatLines] while the "test" runs, then returns [instrumentOutput]. */
class FakeDevice(
    private val logcatLines: List<String>,
    private val instrumentOutput: String = OK_OUTPUT,
    private val bottomScreenshotFails: Boolean = false,
) : Device {
    val acks = mutableListOf<Int>()
    val instrumented = mutableListOf<String>()
    var prepared = false
        private set
    var recordingStopped = false
        private set

    override fun prepare() {
        prepared = true
    }

    override fun startRecording(scenarioDir: File): Recording =
        Recording {
            recordingStopped = true
            Videos(top = File(scenarioDir, TOP_VIDEO).apply { writeText(VIDEO_BYTES) }, bottom = null)
        }

    override fun logcat(): LogStream =
        object : LogStream {
            override val lines: Sequence<String> = logcatLines.asSequence()

            override fun stop() = Unit
        }

    override fun instrument(testClass: String): RunningInstrument {
        instrumented += testClass
        return RunningInstrument { instrumentOutput }
    }

    override fun screenshotTop(to: File) {
        to.writeText(PNG_BYTES)
    }

    override fun screenshotBottom(to: File) {
        if (bottomScreenshotFails) throw IOException("emulator console refused")
        to.writeText(PNG_BYTES)
    }

    override fun ack(index: Int) {
        acks += index
    }

    companion object {
        const val OK_OUTPUT = "OK (1 test)"
        const val TOP_VIDEO = "top.mp4"
        private const val PNG_BYTES = "png"
        private const val VIDEO_BYTES = "video"
    }
}
