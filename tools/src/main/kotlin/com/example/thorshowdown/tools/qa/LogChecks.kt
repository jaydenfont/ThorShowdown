package com.example.thorshowdown.tools.qa

import kotlinx.serialization.Serializable

/** A fake secret the workflow tests use; it must never appear in the logs. */
data class Canary(
    val name: String,
    val value: String,
)

/** Why a log line fails the scenario. */
@Serializable
enum class LogFailureKind { SecretLeaked, Crash, Anr }

/** A log line that fails the scenario. Secrets in [line] are already redacted. */
@Serializable
data class LogFailure(
    val kind: LogFailureKind,
    val line: String,
)

/** What [LogChecks.scan] found: [failures] fail the scenario, [errors] (app error lines) are for review. */
@Serializable
data class LogFindings(
    val failures: List<LogFailure>,
    val errors: List<String>,
)

/**
 * The runner's deterministic log checks for one scenario: leaked [canaries] anywhere, and a crash or
 * ANR of [appPackage]. Error-level lines from the app are listed for review but don't fail on their
 * own, because the platform logs harmless errors too (e.g. OpenGLRenderer on the emulator).
 */
class LogChecks(
    private val appPackage: String,
    private val canaries: List<Canary>,
) {
    /** Scans [lines]; [appPid] is the app's process, or null if it never started. */
    fun scan(
        lines: List<LogcatLine>,
        appPid: Int?,
    ): LogFindings =
        LogFindings(
            failures = lines.mapNotNull { failureIn(it, appPid) },
            errors = lines.filter { it.pid == appPid && it.level == ERROR_LEVEL }.map { redacted(it.raw) },
        )

    private fun failureIn(
        line: LogcatLine,
        appPid: Int?,
    ): LogFailure? {
        val kind =
            when {
                canaries.any { line.raw.contains(it.value) } -> LogFailureKind.SecretLeaked
                line.pid == appPid && line.message.startsWith(FATAL_EXCEPTION) -> LogFailureKind.Crash
                line.message.startsWith("$ANR_PREFIX$appPackage") -> LogFailureKind.Anr
                else -> return null
            }
        return LogFailure(kind, redacted(line.raw))
    }

    private fun redacted(raw: String): String =
        canaries.fold(raw) { text, canary -> text.replace(canary.value, "$REDACTED_OPEN${canary.name}$REDACTED_CLOSE") }

    private companion object {
        const val ERROR_LEVEL = 'E'
        const val FATAL_EXCEPTION = "FATAL EXCEPTION"
        const val ANR_PREFIX = "ANR in "
        const val REDACTED_OPEN = "<"
        const val REDACTED_CLOSE = " canary>"
    }
}
