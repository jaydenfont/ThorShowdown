package com.example.thorshowdown.tools.qa

/**
 * A scenario's spec, `qa/scenarios/<Name>.md`: `# Name`, a summary, then one `## Checkpoint`
 * heading per checkpoint enum constant with what the screens should show.
 */
data class ScenarioSpec(
    val name: String,
    val summary: String,
    val checkpoints: Map<String, String>,
) {
    /**
     * Mismatches between the spec and the checkpoints the test [reached]. Unreached spec
     * checkpoints only count when the scenario [passed]; a failure explains them already.
     */
    fun problems(
        reached: List<String>,
        passed: Boolean,
    ): List<String> {
        val missingHeadings = reached.filter { it !in checkpoints }.map { "$CHECKPOINT_PREFIX$it$NO_HEADING" }
        val neverReached = if (passed) checkpoints.keys.filter { it !in reached }.map { "$SPEC_PREFIX$it$NEVER_REACHED" } else emptyList()
        return missingHeadings + neverReached
    }

    companion object {
        private const val TITLE = "# "
        private const val HEADING = "## "
        private const val CHECKPOINT_PREFIX = "Checkpoint "
        private const val NO_HEADING = " has no heading in the spec"
        private const val SPEC_PREFIX = "Spec checkpoint "
        private const val NEVER_REACHED = " was never reached"
        private const val NEWLINE = "\n"

        fun parse(markdown: String): ScenarioSpec {
            val lines = markdown.lines()
            val name = lines.first { it.startsWith(TITLE) }.removePrefix(TITLE).trim()
            val sections = splitByHeadings(lines.dropWhile { !it.startsWith(TITLE) }.drop(1))
            return ScenarioSpec(name, sections.intro, sections.bodies)
        }

        private class Sections(
            val intro: String,
            val bodies: Map<String, String>,
        )

        private fun splitByHeadings(lines: List<String>): Sections {
            val intro = mutableListOf<String>()
            val bodies = linkedMapOf<String, MutableList<String>>()
            var current: MutableList<String> = intro
            lines.forEach { line ->
                if (line.startsWith(HEADING)) {
                    current = mutableListOf<String>().also { bodies[line.removePrefix(HEADING).trim()] = it }
                } else {
                    current += line
                }
            }
            return Sections(intro.joinTrimmed(), bodies.mapValues { it.value.joinTrimmed() })
        }

        private fun List<String>.joinTrimmed(): String = joinToString(NEWLINE).trim()
    }
}
