package com.example.thorshowdown.tools.qa

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * [Device] on the Android emulator through `adb` ([adb] is the executable, [serial] the emulator).
 * The bottom screen is the emulator's secondary display, which `screencap` can't see, so it is
 * captured through the emulator console (`adb emu screenrecord`), whose console index is 1.
 * The console writes files on the host and can't take paths with spaces, so those land in
 * [staging] first and are moved into the run folder.
 */
class AdbDevice(
    private val adb: File,
    private val serial: String,
    private val instrumentation: String,
    private val staging: File = File(System.getProperty(TMP_DIR_PROPERTY), STAGING_DIR),
) : Device {
    init {
        QaRun.requireEmulator(serial)
        require(SPACE !in staging.absolutePath) { "Emulator console paths can't contain spaces: $staging" }
    }

    override fun prepare() {
        ensureSecondaryDisplay()
        run(SHELL, "input keyevent $WAKEUP_KEY")
        run(SHELL, "rm -rf $ACK_DIR && mkdir -p $ACK_DIR")
        run(LOGCAT, CLEAR)
        staging.deleteRecursively()
        staging.mkdirs()
    }

    private fun ensureSecondaryDisplay() {
        val displays = DISPLAY_LINE.findAll(run(SHELL, GET_DISPLAYS)).count()
        if (displays >= 2) return
        logger.info("Adding the emulator's secondary display")
        run(EMU, ADD_SECONDARY_DISPLAY)
        TimeUnit.MILLISECONDS.sleep(DISPLAY_SETTLE_MILLIS)
    }

    override fun startRecording(scenarioDir: File): Recording {
        run(SHELL, "rm -f $DEVICE_TOP_VIDEO")
        val top = start(SHELL, "screenrecord --time-limit $RECORD_LIMIT_SECONDS $DEVICE_TOP_VIDEO")
        val stagedBottom = File(staging, BOTTOM_VIDEO)
        run(EMU, "screenrecord start --display $BOTTOM_CONSOLE_DISPLAY --time-limit $RECORD_LIMIT_SECONDS ${stagedBottom.absolutePath}")
        return Recording {
            Videos(top = stopTop(top, File(scenarioDir, TOP_VIDEO)), bottom = stopBottom(stagedBottom, File(scenarioDir, BOTTOM_VIDEO)))
        }
    }

    private fun stopTop(
        recorder: Process,
        to: File,
    ): File? =
        try {
            run(SHELL, "pkill -INT screenrecord")
            recorder.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            run(PULL, DEVICE_TOP_VIDEO, to.absolutePath)
            to
        } catch (e: IOException) {
            logger.warning("Top screen video failed: ${e.message}")
            null
        }

    private fun stopBottom(
        staged: File,
        to: File,
    ): File? =
        try {
            run(EMU, "screenrecord stop")
            awaitFile(staged)
            Files.move(staged.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
            to
        } catch (e: IOException) {
            logger.warning("Bottom screen video failed: ${e.message}")
            null
        }

    override fun logcat(): LogStream {
        val process = start(LOGCAT, FORMAT_FLAG, THREADTIME)
        return object : LogStream {
            override val lines: Sequence<String> = process.inputStream.bufferedReader().lineSequence()

            override fun stop() {
                process.destroy()
            }
        }
    }

    override fun instrument(testClass: String): RunningInstrument {
        val process = start(SHELL, "am instrument -w -e class $testClass -e $CAPTURE_ARGUMENT true $instrumentation")
        return RunningInstrument {
            process.inputStream
                .bufferedReader()
                .readText()
                .also { process.waitFor() }
        }
    }

    override fun screenshotTop(to: File) {
        val process = start(EXEC_OUT, SCREENCAP)
        to.outputStream().use { process.inputStream.copyTo(it) }
        if (process.waitFor() != 0 || to.length() == 0L) throw IOException("screencap failed")
    }

    override fun screenshotBottom(to: File) {
        val shots = File(staging, SHOTS_DIR).apply { deleteRecursively() }.apply { mkdirs() }
        run(EMU, "screenrecord screenshot ${shots.absolutePath} --display $BOTTOM_CONSOLE_DISPLAY")
        val shot = awaitAnyFile(shots)
        Files.move(shot.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    override fun ack(index: Int) {
        run(SHELL, "touch $ACK_DIR/$ACK_PREFIX$index")
    }

    /** Runs `adb -s <serial> <args>` and returns its output; throws if it fails or reports `KO`. */
    private fun run(vararg args: String): String {
        val process = start(*args)
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0 || output.startsWith(CONSOLE_ERROR)) throw IOException("adb ${args.joinToString(" ")}: $output")
        return output
    }

    private fun start(vararg args: String): Process =
        ProcessBuilder(listOf(adb.absolutePath, SERIAL_FLAG, serial) + args)
            .redirectErrorStream(true)
            .start()

    /** The emulator writes console captures asynchronously; wait until the file is complete. */
    private fun awaitFile(file: File) {
        poll { file.takeIf { it.exists() && it.length() > 0 && stable(it) } } ?: throw IOException("Never written: $file")
    }

    private fun awaitAnyFile(dir: File): File =
        poll { dir.listFiles()?.firstOrNull { it.length() > 0 && stable(it) } } ?: throw IOException("No screenshot in $dir")

    private fun stable(file: File): Boolean {
        val size = file.length()
        TimeUnit.MILLISECONDS.sleep(STABLE_CHECK_MILLIS)
        return file.length() == size
    }

    private fun <T : Any> poll(check: () -> T?): T? {
        val deadline = System.currentTimeMillis() + FILE_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            check()?.let { return it }
            TimeUnit.MILLISECONDS.sleep(POLL_MILLIS)
        }
        return null
    }

    private companion object {
        val logger: Logger = Logger.getLogger(AdbDevice::class.java.name)
        val DISPLAY_LINE = Regex("""(?m)^\s*Display id \d+""")
        const val TMP_DIR_PROPERTY = "java.io.tmpdir"
        const val STAGING_DIR = "thorshowdown-qa"
        const val SHOTS_DIR = "shots"
        const val SPACE = ' '
        const val SERIAL_FLAG = "-s"
        const val SHELL = "shell"
        const val EMU = "emu"
        const val PULL = "pull"
        const val LOGCAT = "logcat"
        const val EXEC_OUT = "exec-out"
        const val CLEAR = "-c"
        const val FORMAT_FLAG = "-v"
        const val THREADTIME = "threadtime"
        const val SCREENCAP = "screencap -p"
        const val GET_DISPLAYS = "cmd display get-displays"
        const val ADD_SECONDARY_DISPLAY = "multidisplay add 1 1240 1080 400 0"
        const val WAKEUP_KEY = "KEYCODE_WAKEUP"
        const val ACK_DIR = "/data/local/tmp/qa"
        const val ACK_PREFIX = "ack-"
        const val CAPTURE_ARGUMENT = "qaCapture"
        const val DEVICE_TOP_VIDEO = "/sdcard/qa-top.mp4"
        const val TOP_VIDEO = "top.mp4"
        const val BOTTOM_VIDEO = "bottom.webm"
        const val BOTTOM_CONSOLE_DISPLAY = 1
        const val RECORD_LIMIT_SECONDS = 180
        const val STOP_TIMEOUT_SECONDS = 10L
        const val DISPLAY_SETTLE_MILLIS = 2_000L
        const val FILE_TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 100L
        const val STABLE_CHECK_MILLIS = 200L
        const val CONSOLE_ERROR = "KO"
    }
}
