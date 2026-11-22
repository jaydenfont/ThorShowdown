package com.example.thorshowdown.showdown

/** Outcome of [ShowdownClient.login]. */
public sealed interface LoginResult {
    /** Logged in; [username] is the name as the server displays it. */
    public data class Success(
        val username: String,
    ) : LoginResult

    public data class Failure(
        val reason: LoginFailure,
        val message: String,
    ) : LoginResult
}

/** Why a login failed. */
public enum class LoginFailure {
    NotConnected,
    WrongPassword,
    NameTaken,
    Network,
    Timeout,
}
