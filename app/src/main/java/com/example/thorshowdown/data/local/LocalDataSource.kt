package com.example.thorshowdown.data.local

/** On-device storage. For now: the saved login. */
class LocalDataSource(
    private val credentials: CredentialStore,
) {
    /** The saved login, or null if there is none. */
    suspend fun savedLogin(): SavedLogin? = credentials.load()

    /** Saves [login], replacing any previous one. */
    suspend fun saveLogin(login: SavedLogin) = credentials.save(login)

    /** Removes the saved login. */
    suspend fun forgetLogin() = credentials.clear()
}
