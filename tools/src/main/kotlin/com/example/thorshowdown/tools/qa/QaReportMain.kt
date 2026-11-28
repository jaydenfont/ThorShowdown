package com.example.thorshowdown.tools.qa

import java.io.File
import java.util.logging.Logger

private val logger: Logger = Logger.getLogger("QaReport")
private const val REPORT_HTML = "report.html"

/** `:tools:qaReport`: re-renders a run's HTML report, merging the agent's `review.json`. */
fun main(args: Array<String>) {
    val runDir = File(requireNotNull(args.firstOrNull()) { "Usage: qaReport <run folder>" })
    QaRun.rerender(runDir)
    logger.info("Report: ${File(runDir, REPORT_HTML).absolutePath}")
}
