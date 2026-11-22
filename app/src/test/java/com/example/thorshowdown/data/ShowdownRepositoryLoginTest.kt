package com.example.thorshowdown.data

import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.data.local.SavedLogin
import com.example.thorshowdown.fakes.FakeCredentialStore
import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.showdown.LoginFailure
import com.example.thorshowdown.showdown.LoginResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ShowdownRepositoryLoginTest {
    private val client = FakeShowdownClient()
    private val credentials = FakeCredentialStore()
    private val repository = ShowdownRepository(client, LocalDataSource(credentials))

    @Test
    fun ShowdownRepository_BeforeLoadingSavedLogin_StateIsLoading() {
        val state = repository.loginState.value

        assertEquals(LoginState.Loading, state)
    }

    @Test
    fun ShowdownRepository_LoadSavedLoginWithNothingSaved_IsLoggedOutWithoutSavedName() =
        runTest {
            repository.loadSavedLogin()

            assertEquals(LoginState.LoggedOut(savedUsername = null), repository.loginState.value)
        }

    @Test
    fun ShowdownRepository_LoadSavedLoginWithSavedLogin_OffersSavedNameWithoutLoggingIn() =
        runTest {
            credentials.saved = SAVED_LOGIN

            repository.loadSavedLogin()

            assertEquals(LoginState.LoggedOut(savedUsername = SAVED_LOGIN.username), repository.loginState.value)
            assertEquals(emptyList<FakeShowdownClient.Login>(), client.logins)
        }

    @Test
    fun ShowdownRepository_LoginSucceeds_ConnectsSavesLoginAndIsLoggedIn() =
        runTest {
            repository.loadSavedLogin()

            repository.login(NEW_LOGIN.username, NEW_LOGIN.password)

            assertEquals(1, client.connectCount)
            assertEquals(listOf(FakeShowdownClient.Login(NEW_LOGIN.username, NEW_LOGIN.password)), client.logins)
            assertEquals(NEW_LOGIN, credentials.saved)
            assertEquals(LoginState.LoggedIn(FakeShowdownClient.USERNAME), repository.loginState.value)
        }

    @Test
    fun ShowdownRepository_LoginSucceedsWithAnotherSavedLogin_ReplacesSavedLogin() =
        runTest {
            credentials.saved = SavedLogin("Bob", "old-password")
            repository.loadSavedLogin()

            repository.login(NEW_LOGIN.username, NEW_LOGIN.password)

            assertEquals(NEW_LOGIN, credentials.saved)
        }

    @Test
    fun ShowdownRepository_LoginWithSaved_LogsInWithSavedPassword() =
        runTest {
            credentials.saved = SAVED_LOGIN
            repository.loadSavedLogin()

            repository.loginWithSaved()

            assertEquals(listOf(FakeShowdownClient.Login(SAVED_LOGIN.username, SAVED_LOGIN.password)), client.logins)
            assertEquals(LoginState.LoggedIn(FakeShowdownClient.USERNAME), repository.loginState.value)
        }

    @Test
    fun ShowdownRepository_LoginWithSavedWhenNothingSaved_StaysLoggedOutWithoutConnecting() =
        runTest {
            repository.loadSavedLogin()

            repository.loginWithSaved()

            assertEquals(0, client.connectCount)
            assertEquals(LoginState.LoggedOut(savedUsername = null), repository.loginState.value)
        }

    @Test
    fun ShowdownRepository_WrongPasswordForSavedLogin_ForgetsSavedLoginAndReportsError() =
        runTest {
            credentials.saved = SAVED_LOGIN
            client.loginResult = LoginResult.Failure(LoginFailure.WrongPassword, REFUSED_MESSAGE)
            repository.loadSavedLogin()

            repository.loginWithSaved()

            assertNull(credentials.saved)
            assertEquals(
                LoginState.LoggedOut(
                    savedUsername = null,
                    error = LoginError(SAVED_LOGIN.username, LoginFailure.WrongPassword, REFUSED_MESSAGE),
                ),
                repository.loginState.value,
            )
        }

    @Test
    fun ShowdownRepository_WrongPasswordForAnotherName_KeepsSavedLogin() =
        runTest {
            credentials.saved = SAVED_LOGIN
            client.loginResult = LoginResult.Failure(LoginFailure.WrongPassword, REFUSED_MESSAGE)
            repository.loadSavedLogin()

            repository.login("Bob", "guess")

            assertEquals(SAVED_LOGIN, credentials.saved)
            assertEquals(
                LoginState.LoggedOut(
                    savedUsername = SAVED_LOGIN.username,
                    error = LoginError("Bob", LoginFailure.WrongPassword, REFUSED_MESSAGE),
                ),
                repository.loginState.value,
            )
        }

    @Test
    fun ShowdownRepository_NetworkFailureWithSavedLogin_KeepsSavedLoginAndReportsError() =
        runTest {
            credentials.saved = SAVED_LOGIN
            client.loginResult = LoginResult.Failure(LoginFailure.Network, OFFLINE_MESSAGE)
            repository.loadSavedLogin()

            repository.loginWithSaved()

            assertEquals(SAVED_LOGIN, credentials.saved)
            assertEquals(
                LoginState.LoggedOut(
                    savedUsername = SAVED_LOGIN.username,
                    error = LoginError(SAVED_LOGIN.username, LoginFailure.Network, OFFLINE_MESSAGE),
                ),
                repository.loginState.value,
            )
        }

    @Test
    fun ShowdownRepository_ConnectFails_ReportsNetworkErrorWithoutLoggingIn() =
        runTest {
            client.connectFailure = IOException(OFFLINE_MESSAGE)
            repository.loadSavedLogin()

            repository.login(NEW_LOGIN.username, NEW_LOGIN.password)

            assertEquals(emptyList<FakeShowdownClient.Login>(), client.logins)
            assertEquals(
                LoginState.LoggedOut(
                    savedUsername = null,
                    error = LoginError(NEW_LOGIN.username, LoginFailure.Network, OFFLINE_MESSAGE),
                ),
                repository.loginState.value,
            )
        }

    @Test
    fun ShowdownRepository_LoginAwaitingServer_IsLoggingIn() =
        runTest {
            client.loginGate = CompletableDeferred()
            repository.loadSavedLogin()

            launch { repository.login(NEW_LOGIN.username, NEW_LOGIN.password) }
            runCurrent()

            assertEquals(LoginState.LoggingIn(NEW_LOGIN.username), repository.loginState.value)
            client.loginGate?.complete(Unit)
        }

    @Test
    fun ShowdownRepository_LoginWhileAnotherLoginRuns_IgnoresSecondAttempt() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            client.loginGate = gate
            repository.loadSavedLogin()
            val firstLogin = launch { repository.login(NEW_LOGIN.username, NEW_LOGIN.password) }
            runCurrent()

            repository.login("Bob", "other")
            gate.complete(Unit)
            firstLogin.join()

            assertEquals(listOf(FakeShowdownClient.Login(NEW_LOGIN.username, NEW_LOGIN.password)), client.logins)
            assertEquals(LoginState.LoggedIn(FakeShowdownClient.USERNAME), repository.loginState.value)
        }

    private companion object {
        const val REFUSED_MESSAGE = "Wrong password."
        const val OFFLINE_MESSAGE = "offline"
        val NEW_LOGIN = SavedLogin(FakeShowdownClient.USERNAME, "correct horse")
        val SAVED_LOGIN = SavedLogin(FakeShowdownClient.USERNAME, "hunter2")
    }
}
