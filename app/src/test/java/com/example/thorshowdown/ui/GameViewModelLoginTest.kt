package com.example.thorshowdown.ui

import com.example.thorshowdown.data.LoginError
import com.example.thorshowdown.data.ShowdownRepository
import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.data.local.SavedLogin
import com.example.thorshowdown.fakes.FakeCredentialStore
import com.example.thorshowdown.fakes.FakeMoveDex
import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.fakes.FakeWebSession
import com.example.thorshowdown.fakes.MainDispatcherRule
import com.example.thorshowdown.showdown.LoginFailure
import com.example.thorshowdown.showdown.LoginResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class GameViewModelLoginTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val client = FakeShowdownClient()
    private val credentials = FakeCredentialStore()

    /** Where the repository collects client events; cancelled after each test. */
    private val eventScope = CoroutineScope(UnconfinedTestDispatcher())

    @After
    fun cancelEventCollection() {
        eventScope.cancel()
    }

    @Test
    fun GameViewModel_StartWithNothingSaved_ShowsEmptyForm() {
        val viewModel = newViewModel()

        val login = viewModel.bottomState.value.login

        assertEquals(LoginUiState.Form(username = ""), login)
    }

    @Test
    fun GameViewModel_StartWithSavedLogin_OffersSavedLogin() {
        credentials.saved = SAVED_LOGIN

        val viewModel = newViewModel()

        assertEquals(LoginUiState.ChooseSaved(SAVED_LOGIN.username), viewModel.bottomState.value.login)
    }

    @Test
    fun GameViewModel_ContinueAsSaved_LogsInAndShowsUsernameOnBothScreens() {
        credentials.saved = SAVED_LOGIN
        val viewModel = newViewModel()

        viewModel.onAction(UserAction.ContinueAsSaved)

        assertEquals(listOf(FakeShowdownClient.Login(SAVED_LOGIN.username, SAVED_LOGIN.password)), client.logins)
        assertEquals(LoginUiState.LoggedIn(FakeShowdownClient.USERNAME), viewModel.bottomState.value.login)
        assertEquals(FakeShowdownClient.USERNAME, viewModel.topState.value.loggedInUsername)
    }

    @Test
    fun GameViewModel_UseDifferentAccount_ShowsEmptyForm() {
        credentials.saved = SAVED_LOGIN
        val viewModel = newViewModel()

        viewModel.onAction(UserAction.UseDifferentAccount)

        assertEquals(LoginUiState.Form(username = ""), viewModel.bottomState.value.login)
    }

    @Test
    fun GameViewModel_LogInWhileServerResponds_ShowsLoggingInAndTopScreenStaysLoggedOut() {
        client.loginGate = CompletableDeferred()
        val viewModel = newViewModel()

        viewModel.onAction(UserAction.LogIn(TYPED_LOGIN.username, TYPED_LOGIN.password))

        assertEquals(LoginUiState.LoggingIn(TYPED_LOGIN.username), viewModel.bottomState.value.login)
        assertNull(viewModel.topState.value.loggedInUsername)
        client.loginGate?.complete(Unit)
    }

    @Test
    fun GameViewModel_WrongPasswordForSavedLogin_ShowsFormWithNameAndError() {
        credentials.saved = SAVED_LOGIN
        client.loginResult = LoginResult.Failure(LoginFailure.WrongPassword, REFUSED_MESSAGE)
        val viewModel = newViewModel()

        viewModel.onAction(UserAction.ContinueAsSaved)

        assertEquals(
            LoginUiState.Form(
                username = SAVED_LOGIN.username,
                error = LoginError(SAVED_LOGIN.username, LoginFailure.WrongPassword, REFUSED_MESSAGE),
            ),
            viewModel.bottomState.value.login,
        )
    }

    @Test
    fun GameViewModel_NetworkFailureOnSavedLogin_OffersSavedLoginAgainWithError() {
        credentials.saved = SAVED_LOGIN
        client.loginResult = LoginResult.Failure(LoginFailure.Network, OFFLINE_MESSAGE)
        val viewModel = newViewModel()

        viewModel.onAction(UserAction.ContinueAsSaved)

        assertEquals(
            LoginUiState.ChooseSaved(
                username = SAVED_LOGIN.username,
                error = LoginError(SAVED_LOGIN.username, LoginFailure.Network, OFFLINE_MESSAGE),
            ),
            viewModel.bottomState.value.login,
        )
    }

    @Test
    fun GameViewModel_FailureAfterChoosingDifferentAccount_KeepsShowingForm() {
        credentials.saved = SAVED_LOGIN
        client.loginResult = LoginResult.Failure(LoginFailure.NameTaken, TAKEN_MESSAGE)
        val viewModel = newViewModel()
        viewModel.onAction(UserAction.UseDifferentAccount)

        viewModel.onAction(UserAction.LogIn(TYPED_LOGIN.username, TYPED_LOGIN.password))

        assertEquals(
            LoginUiState.Form(
                username = TYPED_LOGIN.username,
                error = LoginError(TYPED_LOGIN.username, LoginFailure.NameTaken, TAKEN_MESSAGE),
            ),
            viewModel.bottomState.value.login,
        )
    }

    private fun newViewModel(): GameViewModel =
        GameViewModel(ShowdownRepository(client, LocalDataSource(credentials), FakeWebSession(), eventScope), FakeMoveDex())

    private companion object {
        val SAVED_LOGIN = SavedLogin(FakeShowdownClient.USERNAME, "hunter2")
        val TYPED_LOGIN = SavedLogin("Bob", "typed-password")
        const val REFUSED_MESSAGE = "Wrong password."
        const val OFFLINE_MESSAGE = "offline"
        const val TAKEN_MESSAGE = "That name is in use."
    }
}
