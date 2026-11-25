package com.example.thorshowdown.qa

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.thorshowdown.MainActivity
import com.example.thorshowdown.ThorShowdownApp
import com.example.thorshowdown.qa.robots.LobbyRobot
import com.example.thorshowdown.qa.robots.LoginRobot
import com.example.thorshowdown.showdown.fakes.FakeShowdownServer
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Sets up one workflow scenario: installs a [FakeAppContainer] before anything uses the app,
 * launches [MainActivity] (both screens), and hands the test its robots and the fake server.
 */
class QaRule(
    private val container: FakeAppContainer = FakeAppContainer(),
) : TestRule {
    private val compose: ComposeTestRule = createEmptyComposeRule()

    /** The scripted Showdown server the app is connected to. */
    val server: FakeShowdownServer get() = container.server

    val login = LoginRobot(compose)
    val lobby = LobbyRobot(compose)

    /** Moments the QA runner screenshots; see [QaCheckpoints]. */
    private val checkpoints = QaCheckpoints(compose)

    fun checkpoint(checkpoint: Enum<*>) = checkpoints.checkpoint(checkpoint)

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement = RuleChain.outerRule(compose).apply(launchingApp(base), description)

    private fun launchingApp(base: Statement): Statement =
        object : Statement() {
            override fun evaluate() {
                ApplicationProvider.getApplicationContext<ThorShowdownApp>().installContainer(container)
                ActivityScenario.launch(MainActivity::class.java).use { base.evaluate() }
            }
        }
}
