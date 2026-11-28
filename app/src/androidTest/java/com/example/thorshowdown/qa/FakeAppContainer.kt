package com.example.thorshowdown.qa

import com.example.thorshowdown.AppContainer
import com.example.thorshowdown.data.local.CredentialStore
import com.example.thorshowdown.data.local.SavedLogin
import com.example.thorshowdown.data.web.WebSession
import com.example.thorshowdown.showdown.fakes.FakeLoginApi
import com.example.thorshowdown.showdown.fakes.FakeShowdownServer

/**
 * The network and device storage for a workflow test: a scripted Showdown server, a login
 * server that hands out canary secrets, and in-memory storage, so the emulator's real saved
 * login and cookies are never touched.
 */
class FakeAppContainer(
    val server: FakeShowdownServer = FakeShowdownServer().apply { autoLogin(Canaries.USERNAME) },
) : AppContainer {
    override val transport = server
    override val loginApi = FakeLoginApi(assertion = Canaries.ASSERTION, sid = Canaries.SID)
    override val credentialStore = InMemoryCredentialStore()
    override val webSession = RecordingWebSession()
}

/** Keeps the saved login in memory for one test process. */
class InMemoryCredentialStore : CredentialStore {
    @Volatile private var saved: SavedLogin? = null

    override suspend fun load(): SavedLogin? = saved

    override suspend fun save(login: SavedLogin) {
        saved = login
    }

    override suspend fun clear() {
        saved = null
    }
}

/** Records sign-ins instead of setting the WebView's real cookie. */
class RecordingWebSession : WebSession {
    @Volatile var signInCount = 0
        private set

    override suspend fun signIn(sid: String) {
        signInCount++
    }
}
