package com.example.thorshowdown.tools.qa

import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * One `qaRun`: runs each scenario whose spec is in [specsDir] (or only those asked for), in name
 * order, into a new `<runsDir>/<yyyyMMdd-HHmmss>/` folder, then writes `report.json`,
 * `report.html` and `report.page.html` there.
 */
class QaRun(
    private val device: Device,
    private val deviceName: String,
    private val logChecks: LogChecks,
    private val testPackage: String,
    private val specsDir: File,
    private val runsDir: File,
    private val commit: String,
) {
    /** Runs the scenarios named in [only], or all of them when it's empty. Returns the run folder. */
    fun run(only: Set<String>): File {
        val specs = selectedSpecs(only)
        val started = LocalDateTime.now()
        val runId = started.format(RUN_ID_FORMAT)
        val runDir = File(runsDir, runId).apply { mkdirs() }
        val runner = ScenarioRunner(device, logChecks, testPackage)
        val scenarios =
            specs.map { spec ->
                logger.info { "Running ${spec.name}" }
                runner.run(spec, File(runDir, spec.name)).also { logger.info { "${spec.name}: ${it.status}" } }
            }
        val report = RunReport(runId, started.format(STARTED_FORMAT), commit, deviceName, scenarios)
        File(runDir, REPORT_JSON).writeText(json.encodeToString(report))
        rerender(runDir)
        return runDir
    }

    private fun selectedSpecs(only: Set<String>): List<ScenarioSpec> {
        val all =
            specsDir
                .listFiles { file -> file.extension == SPEC_EXTENSION }
                .orEmpty()
                .map { ScenarioSpec.parse(it.readText()) }
                .sortedBy { it.name }
        val unknown = only - all.map { it.name }.toSet()
        require(unknown.isEmpty()) { "No spec for $unknown in $specsDir" }
        return if (only.isEmpty()) all else all.filter { it.name in only }
    }

    companion object {
        private val logger: Logger = Logger.getLogger(QaRun::class.java.name)
        private val json = Json { prettyPrint = true }
        private val RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val STARTED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private const val SPEC_EXTENSION = "md"
        private const val EMULATOR_PREFIX = "emulator-"
        private const val REPORT_JSON = "report.json"
        private const val REVIEW_JSON = "review.json"
        private const val REPORT_HTML = "report.html"
        private const val REPORT_PAGE_HTML = "report.page.html"

        /** Workflow tests only ever run on the emulator, never on a plugged-in Thor. */
        fun requireEmulator(serial: String) {
            require(serial.startsWith(EMULATOR_PREFIX)) { "QA runs only target the emulator, not $serial" }
        }

        /** Renders `report.html` and `report.page.html` from the run's JSON, merging `review.json` if present. */
        fun rerender(runDir: File) {
            val report = json.decodeFromString<RunReport>(File(runDir, REPORT_JSON).readText())
            val review = File(runDir, REVIEW_JSON).takeIf { it.exists() }?.let { json.decodeFromString<Review>(it.readText()) }
            val rendered = ReportRenderer.render(report, review)
            File(runDir, REPORT_HTML).writeText(rendered.document)
            File(runDir, REPORT_PAGE_HTML).writeText(rendered.page)
        }
    }
}
