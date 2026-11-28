package com.example.thorshowdown.tools.qa

import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread

/**
 * Runs one scenario on the [device]: records both screens, starts the test, screenshots both screens
 * at each checkpoint marker and acks it, then checks the logs and the spec and writes the evidence
 * into the scenario's folder. The scenario `Name` runs the test class `<testPackage>.<Name>Scenario`.
 */
class ScenarioRunner(
    private val device: Device,
    private val logChecks: LogChecks,
    private val testPackage: String,
) {
    fun run(
        spec: ScenarioSpec,
        dir: File,
    ): ScenarioReport {
        val started = System.currentTimeMillis()
        File(dir, CHECKPOINTS_DIR).mkdirs()
        device.prepare()
        val recording = device.startRecording(dir)
        val session = observe(spec.name, dir)
        val videos = recording.stop()
        return report(spec, dir, session, videos, System.currentTimeMillis() - started)
    }

    /** What happened while the test ran. */
    private class Session(
        val log: List<LogcatLine>,
        val rawLog: List<String>,
        val checkpoints: List<CheckpointCapture>,
        val instrumentOutput: String,
    )

    private fun observe(
        name: String,
        dir: File,
    ): Session {
        val logcat = device.logcat()
        val instrument = device.instrument("$testPackage.$name$SCENARIO_SUFFIX")
        var output = ""
        val waiter =
            thread(name = WAITER_THREAD) {
                output = instrument.await()
                logcat.stop()
            }
        val rawLog = mutableListOf<String>()
        val checkpoints = mutableListOf<CheckpointCapture>()
        logcat.lines.forEach { raw ->
            rawLog += raw
            LogcatLine.parse(raw)?.checkpoint()?.let { checkpoints += capture(it, dir) }
        }
        waiter.join()
        return Session(rawLog.mapNotNull(LogcatLine::parse), rawLog, checkpoints, output)
    }

    /** Screenshots both screens for [mark], then acks it even if a screenshot failed, so the test continues. */
    private fun capture(
        mark: CheckpointMark,
        dir: File,
    ): CheckpointCapture {
        val base = "$CHECKPOINTS_DIR/${mark.index.toString().padStart(2, ZERO)}-${mark.name}"
        val top = screenshot("$base$TOP_SUFFIX", dir, device::screenshotTop)
        val bottom = screenshot("$base$BOTTOM_SUFFIX", dir, device::screenshotBottom)
        device.ack(mark.index)
        return CheckpointCapture(mark.index, mark.name, top, bottom)
    }

    private fun screenshot(
        path: String,
        dir: File,
        take: (File) -> Unit,
    ): String? =
        try {
            take(File(dir, path))
            path
        } catch (e: IOException) {
            logger.log(Level.WARNING, "Screenshot $path failed", e)
            null
        }

    private fun report(
        spec: ScenarioSpec,
        dir: File,
        session: Session,
        videos: Videos,
        durationMillis: Long,
    ): ScenarioReport {
        File(dir, LOGCAT_FILE).writeText(session.rawLog.joinToString(NEWLINE))
        File(dir, INSTRUMENT_FILE).writeText(session.instrumentOutput)
        val result = InstrumentResult.parse(session.instrumentOutput)
        val logs = logChecks.scan(session.log, appPid(session.log))
        val status = if (result.status == ScenarioStatus.Passed && logs.failures.isNotEmpty()) ScenarioStatus.Failed else result.status
        val reached = session.checkpoints.map { it.name }
        return ScenarioReport(
            name = spec.name,
            status = status,
            durationMillis = durationMillis,
            failure = result.failure,
            checkpoints = session.checkpoints,
            logs = logs,
            specProblems = spec.problems(reached, passed = status == ScenarioStatus.Passed),
            spec = SpecSnapshot(spec.summary, spec.checkpoints),
            artifacts =
                ScenarioArtifacts(
                    videos.top?.relativeTo(dir)?.invariantSeparatorsPath,
                    videos.bottom?.relativeTo(dir)?.invariantSeparatorsPath,
                    LOGCAT_FILE,
                    INSTRUMENT_FILE,
                ),
        )
    }

    /** The test runs inside the app's process, so the AndroidJUnitRunner's own log lines give its pid. */
    private fun appPid(log: List<LogcatLine>): Int? = log.firstOrNull { it.tag == TEST_RUNNER_TAG }?.pid

    private companion object {
        val logger: Logger = Logger.getLogger(ScenarioRunner::class.java.name)
        const val SCENARIO_SUFFIX = "Scenario"
        const val CHECKPOINTS_DIR = "checkpoints"
        const val TOP_SUFFIX = "-top.png"
        const val BOTTOM_SUFFIX = "-bottom.png"
        const val ZERO = '0'
        const val LOGCAT_FILE = "logcat.txt"
        const val INSTRUMENT_FILE = "instrument.txt"
        const val TEST_RUNNER_TAG = "TestRunner"
        const val WAITER_THREAD = "qa-instrument-waiter"
        const val NEWLINE = "\n"
    }
}
