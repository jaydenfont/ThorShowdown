package com.example.thorshowdown.showdown

/** Outcome of [ShowdownClient.login]. */
public sealed interface LoginResult {
    /**
     * Logged in; [username] is the name as the server displays it. [sid] is the site's
     * session cookie from the login server (see [LoginResponse]); [toString] redacts it.
     */
    public data class Success(
        val username: String,
        val sid: String?,
    ) : LoginResult {
        override fun toString(): String = "Success(username=$username, sid=$REDACTED)"

        private companion object {
            const val REDACTED = "<redacted>"
        }
    }

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
