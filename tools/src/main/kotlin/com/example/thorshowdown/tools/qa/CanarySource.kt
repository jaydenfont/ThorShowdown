package com.example.thorshowdown.tools.qa

/**
 * Reads the canary secrets from the workflow tests' own `Canaries.kt`, so the runner and the tests
 * can't drift apart. Every `const val` is a secret except the username, which is meant to be shown.
 */
object CanarySource {
    private val CONST = Regex("""const val (\w+) = "([^"]+)"""")
    private const val USERNAME = "USERNAME"

    fun parse(kotlinSource: String): List<Canary> =
        CONST
            .findAll(kotlinSource)
            .map { Canary(it.groupValues[1], it.groupValues[2]) }
            .filter { it.name != USERNAME }
            .toList()
}
