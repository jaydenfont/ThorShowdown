package com.example.thorshowdown.tools.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogChecksTest {
    private val checks = LogChecks(APP_PACKAGE, listOf(PASSWORD))

    @Test
    fun LogChecks_CleanLog_FindsNothing() {
        val findings = checks.scan(lines("I AssetMoveDex: Loaded 954 moves"), APP_PID)

        assertEquals(LogFindings(failures = emptyList(), errors = emptyList()), findings)
    }

    @Test
    fun LogChecks_CanaryInAnyProcess_FailsWithSecretRedacted() {
        val findings = checks.scan(lines("D OkHttp: body name=qa&pass=${PASSWORD.value}", pid = OTHER_PID), APP_PID)

        val failure = findings.failures.single()
        assertEquals(LogFailureKind.SecretLeaked, failure.kind)
        assertFalse(failure.line.contains(PASSWORD.value))
    }

    @Test
    fun LogChecks_FatalExceptionInApp_FailsAsCrash() {
        val findings = checks.scan(lines("E AndroidRuntime: FATAL EXCEPTION: main"), APP_PID)

        assertEquals(listOf(LogFailureKind.Crash), findings.failures.map { it.kind })
    }

    @Test
    fun LogChecks_FatalExceptionInOtherProcess_IsIgnored() {
        val findings = checks.scan(lines("E AndroidRuntime: FATAL EXCEPTION: main", pid = OTHER_PID), APP_PID)

        assertEquals(emptyList<LogFailure>(), findings.failures)
    }

    @Test
    fun LogChecks_AnrForApp_FailsAsAnr() {
        val findings = checks.scan(lines("E ActivityManager: ANR in $APP_PACKAGE (MainActivity)", pid = OTHER_PID), APP_PID)

        assertEquals(listOf(LogFailureKind.Anr), findings.failures.map { it.kind })
    }

    @Test
    fun LogChecks_ErrorLevelFromApp_IsListedForReviewNotFailed() {
        val findings = checks.scan(lines("E OpenGLRenderer: Unable to match the desired swap behavior."), APP_PID)

        assertEquals(emptyList<LogFailure>(), findings.failures)
        assertEquals(1, findings.errors.size)
    }

    @Test
    fun LogChecks_UnknownAppPid_StillFindsCanariesAndAnrs() {
        val log = lines("D Any: ${PASSWORD.value}", pid = OTHER_PID) + lines("E ActivityManager: ANR in $APP_PACKAGE", pid = OTHER_PID)

        val findings = checks.scan(log, appPid = null)

        assertEquals(listOf(LogFailureKind.SecretLeaked, LogFailureKind.Anr), findings.failures.map { it.kind })
    }

    private fun lines(
        levelTagMessage: String,
        pid: Int = APP_PID,
    ): List<LogcatLine> = listOf(requireNotNull(LogcatLine.parse("11-25 08:48:35.316 $pid 1 $levelTagMessage")))

    private companion object {
        const val APP_PACKAGE = "com.example.thorshowdown"
        const val APP_PID = 4242
        const val OTHER_PID = 99
        val PASSWORD = Canary("password", "qa-canary-password-7f3a9c")
    }
}
