package com.example.thorshowdown.showdown

/**
 * What the login server returned for one login attempt.
 *
 * [assertion] may be empty or start with `;;` when the login is refused. [sid] is the site's
 * `sid` session cookie, URL-encoded exactly as the server sent it, or null if it set none.
 * Both are secrets, so [toString] redacts them.
 */
public data class LoginResponse(
    val assertion: String,
    val sid: String?,
) {
    override fun toString(): String = REDACTED_DESCRIPTION

    private companion object {
        const val REDACTED_DESCRIPTION = "LoginResponse(assertion=<redacted>, sid=<redacted>)"
    }
}
