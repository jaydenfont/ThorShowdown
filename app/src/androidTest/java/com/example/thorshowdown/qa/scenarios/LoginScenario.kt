package com.example.thorshowdown.qa.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.thorshowdown.qa.Canaries
import com.example.thorshowdown.qa.QaRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A first-time login with the form, landing in the lobby. Spec: qa/scenarios/Login.md. */
@RunWith(AndroidJUnit4::class)
class LoginScenario {
    @get:Rule val qa = QaRule()

    @Test
    fun Login_FreshLogin_ShowsLobby() {
        qa.login.assertFormShown()
        qa.checkpoint(LoginCheckpoint.FormShown)

        qa.login.logIn(Canaries.USERNAME, Canaries.PASSWORD)

        qa.lobby.assertShownFor(Canaries.USERNAME)
        qa.checkpoint(LoginCheckpoint.LobbyShown)
    }
}

/** Screenshot points; each has a heading in qa/scenarios/Login.md. */
enum class LoginCheckpoint { FormShown, LobbyShown }
