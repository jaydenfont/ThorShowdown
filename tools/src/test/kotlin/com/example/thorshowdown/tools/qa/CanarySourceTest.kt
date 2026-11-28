package com.example.thorshowdown.tools.qa

import org.junit.Assert.assertEquals
import org.junit.Test

class CanarySourceTest {
    @Test
    fun CanarySource_KotlinObject_ReadsSecretsButNotTheUsername() {
        val canaries = CanarySource.parse(SOURCE)

        assertEquals(listOf(Canary("PASSWORD", "qa-canary-password-7f3a9c"), Canary("SID", "qa-canary-sid-c04b16")), canaries)
    }

    private companion object {
        val SOURCE =
            """
            object Canaries {
                const val USERNAME = "qa-player"
                const val PASSWORD = "qa-canary-password-7f3a9c"
                const val SID = "qa-canary-sid-c04b16"
            }
            """.trimIndent()
    }
}
