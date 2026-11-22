package com.example.thorshowdown.data.local

/** On-device storage: session token, teams, settings. */
class LocalDataSource {
    suspend fun loadSessionToken(): String? {
        // TODO: read encrypted token
        return null
    }

    suspend fun saveSessionToken(token: String) {
        // TODO: write encrypted token
    }
}
