package com.example.thorshowdown.qa.robots

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.thorshowdown.ui.TestTags

/** The lobby shown after login (until the main menu, #8). */
class LobbyRobot(
    private val compose: ComposeTestRule,
) {
    /** Waits for the lobby and checks it names [username]. */
    fun assertShownFor(username: String) {
        compose.awaitTag(TestTags.LOBBY_FIND_BATTLE)
        compose.onNodeWithTag(TestTags.LOBBY_USERNAME).assertTextContains(username, substring = true)
    }
}
