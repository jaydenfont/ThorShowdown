package com.example.thorshowdown.tools.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentResultTest {
    @Test
    fun InstrumentResult_OkLine_IsPassed() {
        val result = InstrumentResult.parse("\nTime: 3.2\n\nOK (1 test)\n\n")

        assertEquals(InstrumentResult(ScenarioStatus.Passed, failure = null), result)
    }

    @Test
    fun InstrumentResult_Failures_IsFailedWithTheFailureText() {
        val result = InstrumentResult.parse(FAILED_OUTPUT)

        assertEquals(ScenarioStatus.Failed, result.status)
        assertEquals(
            "1) Login_FreshLogin_ShowsLobby(com.example.thorshowdown.qa.scenarios.LoginScenario)\n" +
                "androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 20000 ms\n" +
                "\tat androidx.compose.ui.test.Foo.bar(Foo.kt:1)",
            result.failure,
        )
    }

    @Test
    fun InstrumentResult_NoInstrumentation_IsErrorWithTheOutput() {
        val output = "INSTRUMENTATION_STATUS: Error=Unable to find instrumentation info\nINSTRUMENTATION_STATUS_CODE: -1"

        val result = InstrumentResult.parse(output)

        assertEquals(ScenarioStatus.Error, result.status)
        assertTrue(result.failure.orEmpty().contains("Unable to find instrumentation info"))
    }

    private companion object {
        val FAILED_OUTPUT =
            """
            Time: 25.1
            There was 1 failure:
            1) Login_FreshLogin_ShowsLobby(com.example.thorshowdown.qa.scenarios.LoginScenario)
            androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 20000 ms
            	at androidx.compose.ui.test.Foo.bar(Foo.kt:1)

            FAILURES!!!
            Tests run: 1,  Failures: 1
            """.trimIndent()
    }
}
