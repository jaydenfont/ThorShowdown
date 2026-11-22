package com.example.thorshowdown.ui

import com.example.thorshowdown.data.LoginError
import com.example.thorshowdown.data.LoginState

/** What the bottom screen shows for login. */
sealed interface LoginUiState {
    /** The saved login is still being read. */
    data object Loading : LoginUiState

    /** "Continue as [username]" or "Use a different account". [error] is why the last attempt failed. */
    data class ChooseSaved(
        val username: String,
        val error: LoginError? = null,
    ) : LoginUiState

    /** Username and password form, prefilled with [username]. */
    data class Form(
        val username: String,
        val error: LoginError? = null,
    ) : LoginUiState

    data class LoggingIn(
        val username: String,
    ) : LoginUiState

    data class LoggedIn(
        val username: String,
    ) : LoginUiState

    companion object {
        private const val EMPTY_USERNAME = ""

        /** Maps the repository's [state]; [useDifferentAccount] is whether the user declined the saved login. */
        fun from(
            state: LoginState,
            useDifferentAccount: Boolean,
        ): LoginUiState =
            when (state) {
                LoginState.Loading -> Loading
                is LoginState.LoggingIn -> LoggingIn(state.username)
                is LoginState.LoggedIn -> LoggedIn(state.username)
                is LoginState.LoggedOut ->
                    if (state.savedUsername != null && !useDifferentAccount) {
                        ChooseSaved(state.savedUsername, state.error)
                    } else {
                        Form(state.error?.username ?: EMPTY_USERNAME, state.error)
                    }
            }
    }
}
