package com.example.thorshowdown.tools.qa

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QaRunTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun QaRun_TwoSpecs_RunsEachAndWritesReports() {
        val device = FakeDevice(listOf(TEST_RUNNER_LINE))
        writeSpecs(LOGIN, BATTLE)

        val runDir = newRun(device).run(only = emptySet())

        assertEquals(listOf("$TEST_PACKAGE.BattleTurnScenario", "$TEST_PACKAGE.LoginScenario"), device.instrumented)
        val report = Json.decodeFromString<RunReport>(File(runDir, "report.json").readText())
        assertEquals(listOf(BATTLE, LOGIN), report.scenarios.map { it.name })
        assertTrue(File(runDir, "report.html").readText().contains("Not reviewed"))
        assertTrue(File(runDir, "report.page.html").exists())
    }

    @Test
    fun QaRun_ScenarioFilter_RunsOnlyThose() {
        val device = FakeDevice(listOf(TEST_RUNNER_LINE))
        writeSpecs(LOGIN, BATTLE)

        newRun(device).run(only = setOf(LOGIN))

        assertEquals(listOf("$TEST_PACKAGE.LoginScenario"), device.instrumented)
    }

    @Test
    fun QaRun_UnknownScenarioInFilter_Throws() {
        writeSpecs(LOGIN)

        assertThrows(IllegalArgumentException::class.java) { newRun(FakeDevice(emptyList())).run(only = setOf("Nope")) }
    }

    @Test
    fun QaRun_NonEmulatorSerial_Refuses() {
        assertThrows(IllegalArgumentException::class.java) { QaRun.requireEmulator("ca99314c") }
    }

    @Test
    fun QaRun_EmulatorSerial_IsAccepted() {
        QaRun.requireEmulator("emulator-5554")
    }

    @Test
    fun QaReport_ReviewAdded_RerenderMergesIt() {
        writeSpecs(LOGIN)
        val runDir = newRun(FakeDevice(listOf(TEST_RUNNER_LINE))).run(only = emptySet())
        File(runDir, "review.json").writeText(Json.encodeToString(Review("qa-skill", "All screens look right.")))

        QaRun.rerender(runDir)

        val html = File(runDir, "report.html").readText()
        assertTrue(html.contains("All screens look right."))
        assertFalse(html.contains("Not reviewed by the QA agent yet"))
    }

    private fun newRun(device: Device): QaRun =
        QaRun(
            device = device,
            deviceName = "emulator-5554",
            logChecks = LogChecks(APP_PACKAGE, emptyList()),
            testPackage = TEST_PACKAGE,
            specsDir = File(temp.root, "specs"),
            runsDir = File(temp.root, "runs"),
            commit = "abc1234",
        )

    private fun writeSpecs(vararg names: String) {
        val dir = File(temp.root, "specs").apply { mkdirs() }
        names.forEach { File(dir, "$it.md").writeText("# $it\n\nSummary.\n") }
    }

    private companion object {
        const val LOGIN = "Login"
        const val BATTLE = "BattleTurn"
        const val APP_PACKAGE = "com.example.thorshowdown"
        const val TEST_PACKAGE = "com.example.thorshowdown.qa.scenarios"
        const val TEST_RUNNER_LINE = "11-25 08:48:30.000 4242 2 I TestRunner: started: x"
    }
}
