package com.example.thorshowdown.fakes

import com.example.thorshowdown.data.web.WebSession

/** Records sign-ins; [onSignIn] lets a test look at the app's state at that moment. */
class FakeWebSession : WebSession {
    val signedInSids = mutableListOf<String>()

    var onSignIn: () -> Unit = {}

    override suspend fun signIn(sid: String) {
        signedInSids += sid
        onSignIn()
    }
}
