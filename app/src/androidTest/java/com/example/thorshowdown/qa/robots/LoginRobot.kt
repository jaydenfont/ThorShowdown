package com.example.thorshowdown.qa.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.thorshowdown.ui.TestTags

/** The bottom-screen login. The only place workflow tests learn how login is laid out. */
class LoginRobot(
    private val compose: ComposeTestRule,
) {
    /** Waits for the username and password form. */
    fun assertFormShown() {
        compose.awaitTag(TestTags.LOGIN_USERNAME)
    }

    /** Fills in the form and submits it. */
    fun logIn(
        username: String,
        password: String,
    ) {
        compose.onNodeWithTag(TestTags.LOGIN_USERNAME).performTextInput(username)
        compose.onNodeWithTag(TestTags.LOGIN_PASSWORD).performTextInput(password)
        compose.onNodeWithTag(TestTags.LOGIN_SUBMIT).performClick()
    }
}
