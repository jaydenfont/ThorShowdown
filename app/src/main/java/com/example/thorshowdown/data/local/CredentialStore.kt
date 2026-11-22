package com.example.thorshowdown.data.local

/** A login saved on the device. [toString] redacts the password so it can't reach logs. */
data class SavedLogin(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "SavedLogin(username=$username, password=$REDACTED)"

    private companion object {
        const val REDACTED = "<redacted>"
    }
}

/** Persists at most one saved login. */
interface CredentialStore {
    /** The saved login, or null if there is none or it can no longer be read. */
    suspend fun load(): SavedLogin?

    /** Replaces any saved login with [login]. */
    suspend fun save(login: SavedLogin)

    /** Removes the saved login, if any. */
    suspend fun clear()
}
