package com.example.thorshowdown.showdown

/** Exchanges credentials and a challstr for a login assertion. */
public interface LoginApi {
    /**
     * Returns the server's assertion. It may be empty or start with `;;` when the login is refused.
     * Throws [java.io.IOException] on network or response-format errors.
     */
    public suspend fun fetchAssertion(
        name: String,
        password: String,
        challstr: String,
    ): String
}
