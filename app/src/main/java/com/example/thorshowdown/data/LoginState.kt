package com.example.thorshowdown.data

import com.example.thorshowdown.showdown.LoginFailure

/** Where the native login stands. */
sealed interface LoginState {
    /** The saved login hasn't been read yet. */
    data object Loading : LoginState

    /** Not logged in. [savedUsername] is the saved login on offer; [error] is why the last attempt failed. */
    data class LoggedOut(
        val savedUsername: String?,
        val error: LoginError? = null,
    ) : LoginState

    data class LoggingIn(
        val username: String,
    ) : LoginState

    data class LoggedIn(
        val username: String,
    ) : LoginState
}

/** A failed login attempt as [username]. */
data class LoginError(
    val username: String,
    val reason: LoginFailure,
    val message: String,
)
