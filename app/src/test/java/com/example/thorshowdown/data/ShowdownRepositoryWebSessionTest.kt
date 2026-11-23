package com.example.thorshowdown.data

import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.fakes.FakeCredentialStore
import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.fakes.FakeWebSession
import com.example.thorshowdown.showdown.LoginFailure
import com.example.thorshowdown.showdown.LoginResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownRepositoryWebSessionTest {
    private val client = FakeShowdownClient()
    private val webSession = FakeWebSession()

    private fun TestScope.newRepository(): ShowdownRepository =
        ShowdownRepository(client, LocalDataSource(FakeCredentialStore()), webSession, backgroundScope)

    @Test
    fun ShowdownRepository_LoginSucceeds_SignsWebViewInWithSid() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = newRepository()

            repository.login(FakeShowdownClient.USERNAME, PASSWORD)

            assertEquals(listOf(FakeShowdownClient.SID), webSession.signedInSids)
        }

    @Test
    fun ShowdownRepository_LoginSucceeds_SignsWebViewInBeforeReportingLoggedIn() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = newRepository()
            var stateAtSignIn: LoginState? = null
            webSession.onSignIn = { stateAtSignIn = repository.loginState.value }

            repository.login(FakeShowdownClient.USERNAME, PASSWORD)

            assertEquals(LoginState.LoggingIn(FakeShowdownClient.USERNAME), stateAtSignIn)
            assertEquals(LoginState.LoggedIn(FakeShowdownClient.USERNAME), repository.loginState.value)
        }

    @Test
    fun ShowdownRepository_LoginWithoutSid_StillLogsInWithoutWebSignIn() =
        runTest(UnconfinedTestDispatcher()) {
            client.loginResult = LoginResult.Success(FakeShowdownClient.USERNAME, sid = null)
            val repository = newRepository()

            repository.login(FakeShowdownClient.USERNAME, PASSWORD)

            assertEquals(emptyList<String>(), webSession.signedInSids)
            assertEquals(LoginState.LoggedIn(FakeShowdownClient.USERNAME), repository.loginState.value)
        }

    @Test
    fun ShowdownRepository_LoginFails_DoesNotSignWebViewIn() =
        runTest(UnconfinedTestDispatcher()) {
            client.loginResult = LoginResult.Failure(LoginFailure.WrongPassword, REFUSED)
            val repository = newRepository()

            repository.login(FakeShowdownClient.USERNAME, PASSWORD)

            assertEquals(emptyList<String>(), webSession.signedInSids)
        }

    private companion object {
        const val PASSWORD = "hunter2"
        const val REFUSED = "Wrong password."
    }
}
