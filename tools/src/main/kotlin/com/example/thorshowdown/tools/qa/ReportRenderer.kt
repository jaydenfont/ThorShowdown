package com.example.thorshowdown.tools.qa

/**
 * The run's report as HTML: [document] is a complete file that opens from disk (`report.html`);
 * [page] is the same content without the document skeleton, for publishing as an artifact
 * (`report.page.html`), where the host supplies `<html>`, `<head>` and `<body>`.
 */
data class RenderedReport(
    val document: String,
    val page: String,
)

/**
 * Renders a [RunReport], plus the agent's [Review] when there is one. Screenshots, videos and logs
 * are linked by path relative to the run folder. All report and review text is HTML-escaped.
 */
object ReportRenderer {
    private const val STYLESHEET = "/qa/report.css"
    private const val FONTS =
        "https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;600&display=swap"
    private const val DOCUMENT_START = "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
    private const val VIEWPORT = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
    private const val HEAD_END_BODY_START = "</head>\n<body>\n"
    private const val DOCUMENT_END = "</body>\n</html>\n"

    private val css: String by lazy {
        requireNotNull(ReportRenderer::class.java.getResource(STYLESHEET)) { "Missing $STYLESHEET" }.readText()
    }

    fun render(
        report: RunReport,
        review: Review?,
    ): RenderedReport {
        val head = head(report)
        val body = body(report, review)
        return RenderedReport(
            document = DOCUMENT_START + VIEWPORT + head + HEAD_END_BODY_START + body + DOCUMENT_END,
            page = head + body,
        )
    }

    private fun head(report: RunReport): String =
        buildString {
            append("<title>${Labels.TITLE_PREFIX}${esc(report.runId)}</title>\n")
            append("<meta name=\"claude:viewport-fit-sides\" content=\"cover\">\n")
            append("<link rel=\"stylesheet\" href=\"$FONTS\">\n")
            append("<style>\n$css</style>\n")
        }

    private fun body(
        report: RunReport,
        review: Review?,
    ): String =
        buildString {
            append("<main class=\"wrap\">\n")
            append(header(report))
            append(summary(report, review))
            append(scenarioTable(report, review))
            report.scenarios.forEach { append(scenarioSection(it, review?.scenarios?.get(it.name))) }
            append("</main>\n")
        }

    private fun header(report: RunReport): String =
        """
        <header>
          <span class="eyebrow">${Labels.EYEBROW}</span>
          <h1>${Labels.RUN} ${esc(report.runId)}</h1>
          <dl class="meta">
            ${metaItem(Labels.STARTED, report.startedAt)}
            ${metaItem(Labels.COMMIT, report.commit)}
            ${metaItem(Labels.DEVICE, report.device)}
            ${metaItem(Labels.TOTAL_TIME, seconds(report.scenarios.sumOf { it.durationMillis }))}
          </dl>
        </header>

        """.trimIndent()

    private fun metaItem(
        label: String,
        value: String,
    ): String = "<div><dt>$label</dt><dd>${esc(value)}</dd></div>"

    private fun summary(
        report: RunReport,
        review: Review?,
    ): String {
        val total = report.scenarios.size.coerceAtLeast(1)
        val counts = ScenarioStatus.entries.associateWith { status -> report.scenarios.count { it.status == status } }
        val pills =
            counts.filterValues { it > 0 }.entries.joinToString("") { (status, n) ->
                "<span class=\"pill ${statusClass(status)}\">$n ${statusWord(status)}</span>"
            }
        val bar =
            counts.filterValues { it > 0 }.entries.joinToString("") { (status, n) ->
                "<span class=\"${statusClass(status)}\" style=\"width:${n * PERCENT / total}%\"></span>"
            }
        val note =
            if (review == null) {
                "<p class=\"review-note muted\">${Labels.NOT_REVIEWED_RUN}</p>"
            } else {
                "<p class=\"review-note\"><strong>${Labels.REVIEWED_BY} ${esc(review.reviewer)}.</strong> ${esc(review.summary)}</p>"
            }
        return """
            <section class="summary" aria-label="${Labels.SUMMARY}">
              <div class="counts">$pills</div>
              <div class="bar" role="img" aria-label="${Labels.SUMMARY}">$bar</div>
              $note
            </section>

            """.trimIndent()
    }

    private fun scenarioTable(
        report: RunReport,
        review: Review?,
    ): String {
        val rows =
            report.scenarios.joinToString("\n") { scenario ->
                val verdicts = screenVerdicts(scenario, review?.scenarios?.get(scenario.name))
                """
                <tr>
                  <td><a href="#${anchor(scenario.name)}">${esc(scenario.name)}</a></td>
                  <td>${statusPill(scenario.status)}</td>
                  <td class="num">${seconds(scenario.durationMillis)}</td>
                  <td class="num">${scenario.checkpoints.size}</td>
                  <td class="num">${scenario.logs.failures.size}</td>
                  <td>${verdictTally(verdicts)}</td>
                </tr>
                """.trimIndent()
            }
        return """
            <section aria-label="${Labels.SCENARIOS}">
              <div class="table-scroll">
                <table>
                  <thead><tr>
                    <th>${Labels.SCENARIO}</th><th>${Labels.RESULT}</th><th>${Labels.TIME}</th>
                    <th>${Labels.CHECKPOINTS}</th><th>${Labels.LOG_FAILURES}</th><th>${Labels.VISUAL_REVIEW}</th>
                  </tr></thead>
                  <tbody>
            $rows
                  </tbody>
                </table>
              </div>
            </section>

            """.trimIndent()
    }

    /** Every screenshot's verdict in [scenario], null where it isn't reviewed. */
    private fun screenVerdicts(
        scenario: ScenarioReport,
        review: ScenarioReview?,
    ): List<Verdict?> =
        scenario.checkpoints.flatMap { checkpoint ->
            val checkpointReview = review?.checkpoints?.get(checkpoint.name)
            listOf(checkpointReview?.top?.verdict, checkpointReview?.bottom?.verdict)
        }

    private fun verdictTally(verdicts: List<Verdict?>): String {
        if (verdicts.isEmpty()) return "<span class=\"muted\">${Labels.NONE}</span>"
        return verdicts
            .groupingBy { it }
            .eachCount()
            .entries
            .joinToString(" ") { (verdict, n) -> "<span class=\"pill ${verdictClass(verdict)}\">$n ${verdictWord(verdict)}</span>" }
    }

    private fun scenarioSection(
        scenario: ScenarioReport,
        review: ScenarioReview?,
    ): String =
        buildString {
            append("<section class=\"scenario\" id=\"${anchor(scenario.name)}\">\n")
            append("<div class=\"scenario-head\"><h2>${esc(scenario.name)}</h2>${statusPill(scenario.status)}")
            append("<span class=\"muted mono\">${seconds(scenario.durationMillis)}</span>")
            append("<p>${esc(scenario.spec.summary)}</p></div>\n")
            append(problems(scenario))
            review?.notes?.let { append("<p class=\"review-note\">${esc(it)}</p>\n") }
            review?.logs?.let { append("<p class=\"review-note\">${Labels.LOGS}: ${verdictPill(it.verdict)} ${esc(it.reason)}</p>\n") }
            scenario.checkpoints.forEach { append(checkpoint(scenario, it, review?.checkpoints?.get(it.name))) }
            append(evidence(scenario))
            append("</section>\n")
        }

    private fun problems(scenario: ScenarioReport): String {
        val items =
            buildList {
                scenario.failure?.let { add("<div class=\"problem\"><strong>${Labels.TEST_FAILURE}</strong><pre>${esc(it)}</pre></div>") }
                scenario.logs.failures.forEach {
                    add("<p class=\"problem\"><strong>${logFailureWord(it.kind)}</strong> <code>${esc(it.line)}</code></p>")
                }
                scenario.specProblems.forEach { add("<p class=\"problem\"><strong>${Labels.SPEC}</strong> ${esc(it)}</p>") }
            }
        return if (items.isEmpty()) "" else "<div class=\"problems\">${items.joinToString("\n")}</div>\n"
    }

    private fun checkpoint(
        scenario: ScenarioReport,
        capture: CheckpointCapture,
        review: CheckpointReview?,
    ): String {
        val index = capture.index.toString().padStart(INDEX_WIDTH, ZERO)
        return buildString {
            append("<article class=\"checkpoint\">\n")
            append("<div class=\"checkpoint-head\"><span class=\"index\">$index</span><h3>${esc(capture.name)}</h3></div>\n")
            append(expectation(scenario.spec.checkpoints[capture.name]))
            append("\n<div class=\"screens\">\n")
            append(screen(scenario.name, capture.top, Labels.TOP_SCREEN, TOP_CLASS, review?.top))
            append(screen(scenario.name, capture.bottom, Labels.BOTTOM_SCREEN, BOTTOM_CLASS, review?.bottom))
            append("</div>\n</article>\n")
        }
    }

    /** Spec text is a markdown bullet list; shown as a list, anything else as a paragraph. */
    private fun expectation(spec: String?): String {
        if (spec == null) return "<p class=\"expect\">${Labels.NO_SPEC}</p>"
        val bullets = spec.lines().filter { it.isNotBlank() }
        return if (bullets.all { it.startsWith(BULLET) }) {
            "<ul class=\"expect\">${bullets.joinToString("") { "<li>${esc(it.removePrefix(BULLET))}</li>" }}</ul>"
        } else {
            "<p class=\"expect\">${esc(spec)}</p>"
        }
    }

    private fun screen(
        scenarioName: String,
        path: String?,
        label: String,
        cssClass: String,
        review: ScreenReview?,
    ): String {
        val image =
            if (path == null) {
                "<div class=\"missing\">${Labels.SCREENSHOT_MISSING}</div>"
            } else {
                val src = esc("$scenarioName/$path")
                "<a href=\"$src\"><img src=\"$src\" alt=\"${esc(label)}\" loading=\"lazy\"></a>"
            }
        val reason = review?.let { "<span class=\"reason\">${esc(it.reason)}</span>" }.orEmpty()
        val caption = "<span class=\"label\"><span class=\"muted\">$label</span>${verdictPill(review?.verdict)}</span>$reason"
        return "<figure class=\"$cssClass\">$image<figcaption>$caption</figcaption></figure>\n"
    }

    private fun evidence(scenario: ScenarioReport): String {
        val artifacts = scenario.artifacts
        val links =
            listOfNotNull(
                artifacts.topVideo?.let { Labels.TOP_VIDEO to it },
                artifacts.bottomVideo?.let { Labels.BOTTOM_VIDEO to it },
                Labels.LOGCAT to artifacts.logcat,
                Labels.INSTRUMENT to artifacts.instrument,
            ).joinToString("") { (label, path) -> "<li><a href=\"${esc("${scenario.name}/$path")}\">$label</a></li>" }
        val errors =
            if (scenario.logs.errors.isEmpty()) {
                ""
            } else {
                "<details><summary>${scenario.logs.errors.size} ${Labels.ERROR_LINES}</summary>" +
                    "<pre>${esc(scenario.logs.errors.joinToString("\n"))}</pre></details>"
            }
        return "<ul class=\"evidence\">$links</ul>\n$errors\n"
    }

    private fun statusPill(status: ScenarioStatus): String = "<span class=\"pill ${statusClass(status)}\">${statusWord(status)}</span>"

    private fun verdictPill(verdict: Verdict?): String = "<span class=\"pill ${verdictClass(verdict)}\">${verdictWord(verdict)}</span>"

    private fun statusClass(status: ScenarioStatus): String =
        when (status) {
            ScenarioStatus.Passed -> "s-passed"
            ScenarioStatus.Failed -> "s-failed"
            ScenarioStatus.Error -> "s-error"
        }

    private fun statusWord(status: ScenarioStatus): String =
        when (status) {
            ScenarioStatus.Passed -> Labels.PASSED
            ScenarioStatus.Failed -> Labels.FAILED
            ScenarioStatus.Error -> Labels.ERROR
        }

    private fun verdictClass(verdict: Verdict?): String =
        when (verdict) {
            Verdict.Pass -> "verdict-pass"
            Verdict.Fail -> "verdict-fail"
            Verdict.NeedsHuman -> "verdict-needshuman"
            null -> "verdict-none"
        }

    private fun verdictWord(verdict: Verdict?): String =
        when (verdict) {
            Verdict.Pass -> Labels.LOOKS_RIGHT
            Verdict.Fail -> Labels.LOOKS_WRONG
            Verdict.NeedsHuman -> Labels.NEEDS_HUMAN
            null -> Labels.NOT_REVIEWED
        }

    private fun logFailureWord(kind: LogFailureKind): String =
        when (kind) {
            LogFailureKind.SecretLeaked -> Labels.SECRET_LEAKED
            LogFailureKind.Crash -> Labels.CRASH
            LogFailureKind.Anr -> Labels.ANR
        }

    private fun seconds(millis: Long): String = String.format(java.util.Locale.ROOT, SECONDS_FORMAT, millis / MILLIS_PER_SECOND)

    private fun anchor(name: String): String = "$ANCHOR_PREFIX${name.filter { it.isLetterOrDigit() }}"

    /** Escapes text for HTML element content and double-quoted attributes. */
    private fun esc(text: String): String =
        buildString(text.length) {
            text.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }

    private const val PERCENT = 100.0
    private const val MILLIS_PER_SECOND = 1000.0
    private const val SECONDS_FORMAT = "%.1f s"
    private const val BULLET = "- "
    private const val INDEX_WIDTH = 2
    private const val ZERO = '0'
    private const val ANCHOR_PREFIX = "scenario-"
    private const val TOP_CLASS = "screen-top"
    private const val BOTTOM_CLASS = "screen-bottom"

    /** Words the reader sees. */
    private object Labels {
        const val TITLE_PREFIX = "QA run "
        const val EYEBROW = "ThorShowdown workflow tests"
        const val RUN = "Run"
        const val STARTED = "Started"
        const val COMMIT = "Commit"
        const val DEVICE = "Device"
        const val TOTAL_TIME = "Total time"
        const val SUMMARY = "Summary"
        const val SCENARIOS = "Scenarios"
        const val SCENARIO = "Scenario"
        const val RESULT = "Result"
        const val TIME = "Time"
        const val CHECKPOINTS = "Checkpoints"
        const val LOG_FAILURES = "Log failures"
        const val VISUAL_REVIEW = "Visual review"
        const val NONE = "None"
        const val PASSED = "passed"
        const val FAILED = "failed"
        const val ERROR = "could not run"
        const val LOOKS_RIGHT = "Looks right"
        const val LOOKS_WRONG = "Looks wrong"
        const val NEEDS_HUMAN = "Needs a person"
        const val NOT_REVIEWED = "Not reviewed"
        const val NOT_REVIEWED_RUN =
            "Not reviewed by the QA agent yet. Code assertions and log checks are final; screenshots are unjudged."
        const val REVIEWED_BY = "Reviewed by"
        const val LOGS = "Logs"
        const val TEST_FAILURE = "Test failure"
        const val SPEC = "Spec"
        const val SECRET_LEAKED = "Secret in logs"
        const val CRASH = "Crash"
        const val ANR = "App not responding"
        const val NO_SPEC = "No spec text for this checkpoint."
        const val TOP_SCREEN = "Top · display 0 · 1920×1080"
        const val BOTTOM_SCREEN = "Bottom · display 2 · 1240×1080"
        const val SCREENSHOT_MISSING = "Screenshot missing"
        const val TOP_VIDEO = "Top screen video"
        const val BOTTOM_VIDEO = "Bottom screen video"
        const val LOGCAT = "Logcat"
        const val INSTRUMENT = "Test runner output"
        const val ERROR_LINES = "error lines from the app (for review, not failures)"
    }
}
