package com.example.thorshowdown.tools.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScenarioRunnerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun ScenarioRunner_Checkpoint_CapturesBothScreensThenAcks() {
        val device = FakeDevice(listOf(TEST_RUNNER_LINE, marker(1, FORM_SHOWN)))

        val report = run(device)

        assertEquals(listOf(1), device.acks)
        val capture = report.checkpoints.single()
        assertEquals(
            CheckpointCapture(1, FORM_SHOWN, top = "checkpoints/01-FormShown-top.png", bottom = "checkpoints/01-FormShown-bottom.png"),
            capture,
        )
        assertTrue(File(scenarioDir(), capture.top!!).exists())
    }

    @Test
    fun ScenarioRunner_TestPasses_ReportsPassedWithLogsAndVideos() {
        val device = FakeDevice(listOf(TEST_RUNNER_LINE, marker(1, FORM_SHOWN), marker(2, LOBBY_SHOWN)))

        val report = run(device)

        assertEquals(ScenarioStatus.Passed, report.status)
        assertEquals(listOf(LOGIN_TEST_CLASS), device.instrumented)
        assertTrue(device.prepared && device.recordingStopped)
        assertEquals(
            ScenarioArtifacts(topVideo = FakeDevice.TOP_VIDEO, bottomVideo = null, logcat = "logcat.txt", instrument = "instrument.txt"),
            report.artifacts,
        )
        assertTrue(File(scenarioDir(), "logcat.txt").readText().contains("QA_CHECKPOINT 2 LobbyShown"))
    }

    @Test
    fun ScenarioRunner_CanaryInLogOfPassingTest_FailsScenario() {
        val device = FakeDevice(listOf(TEST_RUNNER_LINE, appLine("D Login: password=${CANARY.value}")))

        val report = run(device)

        assertEquals(ScenarioStatus.Failed, report.status)
        assertEquals(listOf(LogFailureKind.SecretLeaked), report.logs.failures.map { it.kind })
    }

    @Test
    fun ScenarioRunner_TestFails_KeepsTheFailureText() {
        val device = FakeDevice(listOf(TEST_RUNNER_LINE), instrumentOutput = "There was 1 failure:\n1) boom\nFAILURES!!!")

        val report = run(device)

        assertEquals(ScenarioStatus.Failed, report.status)
        assertEquals("1) boom", report.failure)
    }

    @Test
    fun ScenarioRunner_BottomScreenshotFails_RecordsItMissingAndStillAcks() {
        val device = FakeDevice(listOf(TEST_RUNNER_LINE, marker(1, FORM_SHOWN)), bottomScreenshotFails = true)

        val report = run(device)

        assertNull(report.checkpoints.single().bottom)
        assertEquals(listOf(1), device.acks)
    }

    @Test
    fun ScenarioRunner_CheckpointMissingFromSpec_ReportsSpecProblem() {
        val device = FakeDevice(listOf(TEST_RUNNER_LINE, marker(1, FORM_SHOWN), marker(2, "Surprise"), marker(3, LOBBY_SHOWN)))

        val report = run(device)

        assertEquals(listOf("Checkpoint Surprise has no heading in the spec"), report.specProblems)
    }

    private fun run(device: Device): ScenarioReport =
        ScenarioRunner(device, LogChecks(APP_PACKAGE, listOf(CANARY)), TEST_PACKAGE).run(SPEC, scenarioDir())

    private fun scenarioDir(): File = File(temp.root, SPEC.name)

    private fun marker(
        index: Int,
        name: String,
    ): String = appLine("I QaCheckpoint: QA_CHECKPOINT $index $name")

    private fun appLine(levelTagMessage: String): String = "11-25 08:48:35.316 $APP_PID 1 $levelTagMessage"

    private companion object {
        const val APP_PACKAGE = "com.example.thorshowdown"
        const val TEST_PACKAGE = "com.example.thorshowdown.qa.scenarios"
        const val LOGIN_TEST_CLASS = "$TEST_PACKAGE.LoginScenario"
        const val APP_PID = 4242
        const val FORM_SHOWN = "FormShown"
        const val LOBBY_SHOWN = "LobbyShown"
        const val TEST_RUNNER_LINE = "11-25 08:48:30.000 $APP_PID 2 I TestRunner: started: Login_FreshLogin_ShowsLobby"
        val CANARY = Canary("password", "qa-canary-password-7f3a9c")
        val SPEC = ScenarioSpec("Login", "Logs in.", mapOf(FORM_SHOWN to "- form", LOBBY_SHOWN to "- lobby"))
    }
}
