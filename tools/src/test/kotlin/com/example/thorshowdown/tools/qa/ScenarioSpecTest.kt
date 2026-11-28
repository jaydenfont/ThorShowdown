package com.example.thorshowdown.tools.qa

import org.junit.Assert.assertEquals
import org.junit.Test

class ScenarioSpecTest {
    @Test
    fun ScenarioSpec_Markdown_ParsesNameSummaryAndCheckpoints() {
        val spec = ScenarioSpec.parse(SPEC)

        assertEquals(
            ScenarioSpec(
                name = "Login",
                summary = "A first-time login.\nTest: LoginScenario.kt.",
                checkpoints =
                    mapOf(
                        "FormShown" to "- Bottom: the form.\n- Top: idle.",
                        "LobbyShown" to "- Bottom: the lobby.",
                    ),
            ),
            spec,
        )
    }

    @Test
    fun ScenarioSpec_ReachedEveryCheckpoint_HasNoProblems() {
        val problems = ScenarioSpec.parse(SPEC).problems(reached = listOf("FormShown", "LobbyShown"), passed = true)

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun ScenarioSpec_ReachedCheckpointNotInSpec_IsAProblem() {
        val problems = ScenarioSpec.parse(SPEC).problems(reached = listOf("FormShown", "Surprise", "LobbyShown"), passed = true)

        assertEquals(listOf("Checkpoint Surprise has no heading in the spec"), problems)
    }

    @Test
    fun ScenarioSpec_PassedWithoutReachingSpecCheckpoint_IsAProblem() {
        val problems = ScenarioSpec.parse(SPEC).problems(reached = listOf("FormShown"), passed = true)

        assertEquals(listOf("Spec checkpoint LobbyShown was never reached"), problems)
    }

    @Test
    fun ScenarioSpec_FailedBeforeLaterCheckpoints_IsNotASpecProblem() {
        val problems = ScenarioSpec.parse(SPEC).problems(reached = listOf("FormShown"), passed = false)

        assertEquals(emptyList<String>(), problems)
    }

    private companion object {
        val SPEC =
            """
            # Login

            A first-time login.
            Test: LoginScenario.kt.

            ## FormShown
            - Bottom: the form.
            - Top: idle.

            ## LobbyShown
            - Bottom: the lobby.
            """.trimIndent()
    }
}
