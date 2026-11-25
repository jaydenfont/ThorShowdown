package com.example.thorshowdown.qa.robots

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

    fun findBattle() {
        compose.onNodeWithTag(TestTags.LOBBY_FIND_BATTLE).performClick()
    }

    /** Waits for the searching state with its Cancel button. */
    fun assertSearching() {
        compose.awaitTag(TestTags.LOBBY_SEARCHING)
        compose.awaitTag(TestTags.LOBBY_CANCEL_SEARCH)
    }

    fun cancelSearch() {
        compose.onNodeWithTag(TestTags.LOBBY_CANCEL_SEARCH).performClick()
    }

    /** Waits for the idle lobby: Find a Random Battle is back. */
    fun assertIdle() {
        compose.awaitTag(TestTags.LOBBY_FIND_BATTLE)
    }
}
