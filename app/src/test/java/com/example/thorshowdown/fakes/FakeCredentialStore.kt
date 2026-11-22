package com.example.thorshowdown.fakes

import com.example.thorshowdown.data.local.CredentialStore
import com.example.thorshowdown.data.local.SavedLogin

/** In-memory [CredentialStore]. */
class FakeCredentialStore(
    var saved: SavedLogin? = null,
) : CredentialStore {
    override suspend fun load(): SavedLogin? = saved

    override suspend fun save(login: SavedLogin) {
        saved = login
    }

    override suspend fun clear() {
        saved = null
    }
}
