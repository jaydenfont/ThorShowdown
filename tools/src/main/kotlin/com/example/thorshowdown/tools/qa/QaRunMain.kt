package com.example.thorshowdown.tools.qa

import java.io.File
import java.util.logging.Logger

private val logger: Logger = Logger.getLogger("QaRun")

/** Arguments `qaRun` passes, as `name=value`. */
private enum class RunArg(
    val key: String,
) {
    Adb("adb"),
    Serial("serial"),
    Specs("specs"),
    Runs("runs"),
    Canaries("canaries"),
    Commit("commit"),
    Scenarios("scenarios"),
}

private const val APP_PACKAGE = "com.example.thorshowdown"
private const val TEST_PACKAGE = "$APP_PACKAGE.qa.scenarios"
private const val INSTRUMENTATION = "$APP_PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
private const val ARG_SEPARATOR = "="
private const val LIST_SEPARATOR = ","
private const val REPORT_HTML = "report.html"

/** `:tools:qaRun`: runs the workflow scenarios on the emulator and writes the report. */
fun main(args: Array<String>) {
    val parsed = args.associate { it.substringBefore(ARG_SEPARATOR) to it.substringAfter(ARG_SEPARATOR) }

    fun arg(name: RunArg): String = requireNotNull(parsed[name.key]) { "Missing ${name.key}=" }
    val serial = arg(RunArg.Serial)
    val run =
        QaRun(
            device = AdbDevice(File(arg(RunArg.Adb)), serial, INSTRUMENTATION),
            deviceName = serial,
            logChecks = LogChecks(APP_PACKAGE, CanarySource.parse(File(arg(RunArg.Canaries)).readText())),
            testPackage = TEST_PACKAGE,
            specsDir = File(arg(RunArg.Specs)),
            runsDir = File(arg(RunArg.Runs)),
            commit = arg(RunArg.Commit),
        )
    val only =
        parsed[RunArg.Scenarios.key]
            .orEmpty()
            .split(LIST_SEPARATOR)
            .filter { it.isNotBlank() }
            .toSet()
    val runDir = run.run(only)
    logger.info("Report: ${File(runDir, REPORT_HTML).absolutePath}")
}
