package com.example.thorshowdown.tools.qa

import kotlinx.serialization.Serializable

/** How a scenario ended: its test passed, its test failed, or it couldn't run properly. */
@Serializable
enum class ScenarioStatus { Passed, Failed, Error }

/** The outcome of one `am instrument -w` run, with the failure text when it didn't pass. */
data class InstrumentResult(
    val status: ScenarioStatus,
    val failure: String?,
) {
    companion object {
        private val OK = Regex("""(?m)^OK \(\d+ tests?\)$""")
        private val FAILURE_START = Regex("""(?m)^There (was|were) \d+ failures?:$""")
        private const val FAILURES_END = "FAILURES!!!"

        fun parse(output: String): InstrumentResult =
            when {
                OK.containsMatchIn(output) -> InstrumentResult(ScenarioStatus.Passed, failure = null)
                FAILURES_END in output -> InstrumentResult(ScenarioStatus.Failed, failureText(output))
                else -> InstrumentResult(ScenarioStatus.Error, output.trim())
            }

        private fun failureText(output: String): String {
            val start =
                FAILURE_START
                    .find(output)
                    ?.range
                    ?.last
                    ?.plus(1) ?: 0
            return output.substring(start, output.indexOf(FAILURES_END)).trim()
        }
    }
}
