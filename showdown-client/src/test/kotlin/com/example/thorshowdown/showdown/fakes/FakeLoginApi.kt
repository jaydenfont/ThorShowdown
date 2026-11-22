package com.example.thorshowdown.showdown.fakes

import com.example.thorshowdown.showdown.LoginApi
import com.example.thorshowdown.showdown.LoginResponse
import java.io.IOException

/** In-memory stand-in for the Showdown login server. */
class FakeLoginApi(
    private val assertion: String = VALID_ASSERTION,
    private val failure: IOException? = null,
    private val sid: String? = VALID_SID,
) : LoginApi {
    /** Arguments of each call, in order. */
    val calls = mutableListOf<Call>()

    override suspend fun fetchAssertion(
        name: String,
        password: String,
        challstr: String,
    ): LoginResponse {
        calls += Call(name, password, challstr)
        failure?.let { throw it }
        return LoginResponse(assertion, sid)
    }

    data class Call(
        val name: String,
        val password: String,
        val challstr: String,
    )

    companion object {
        const val VALID_ASSERTION = "ASSERT-123"
        const val VALID_SID = "alice%2C1%2Csessiontoken"
    }
}
