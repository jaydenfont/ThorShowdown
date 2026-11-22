package com.example.thorshowdown.showdown

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder

class OkHttpLoginApiTest {
    private val loginServer = MockWebServer()

    @Before
    fun startServer() {
        loginServer.start()
    }

    @After
    fun stopServer() {
        loginServer.close()
    }

    @Test
    fun OkHttpLoginApi_ValidLogin_PostsFormAndReturnsAssertion() =
        runTest {
            loginServer.enqueue(MockResponse.Builder().body("""]{"actionsuccess":true,"assertion":"ASSERT-123"}""").build())
            val api = OkHttpLoginApi(config())

            val assertion = api.fetchAssertion("Alice", "p&ss word", "4|abc")

            val request = loginServer.takeRequest()
            assertEquals("ASSERT-123", assertion)
            assertEquals("POST", request.method)
            assertEquals(USER_AGENT, request.headers["User-Agent"])
            assertEquals(
                mapOf("name" to "Alice", "pass" to "p&ss word", "challstr" to "4|abc"),
                decodeForm(request.body?.utf8().orEmpty()),
            )
        }

    @Test
    fun OkHttpLoginApi_ResponseWithoutAssertion_ReturnsEmptyAssertion() =
        runTest {
            loginServer.enqueue(MockResponse.Builder().body("""]{"actionsuccess":false}""").build())
            val api = OkHttpLoginApi(config())

            val assertion = api.fetchAssertion("Alice", "wrong", "4|abc")

            assertEquals("", assertion)
        }

    @Test
    fun OkHttpLoginApi_HttpErrorStatus_ThrowsIOException() =
        runTest {
            loginServer.enqueue(
                MockResponse
                    .Builder()
                    .code(HTTP_SERVER_ERROR)
                    .body("oops")
                    .build(),
            )
            val api = OkHttpLoginApi(config())

            val error = runCatching { api.fetchAssertion("Alice", "pw", "4|abc") }.exceptionOrNull()

            assertTrue(error is IOException)
        }

    @Test
    fun OkHttpLoginApi_UnreadableBody_ThrowsIOException() =
        runTest {
            loginServer.enqueue(MockResponse.Builder().body("<html>maintenance</html>").build())
            val api = OkHttpLoginApi(config())

            val error = runCatching { api.fetchAssertion("Alice", "pw", "4|abc") }.exceptionOrNull()

            assertTrue(error is IOException)
        }

    /** Decodes an application/x-www-form-urlencoded body into its fields. */
    private fun decodeForm(body: String): Map<String, String> =
        body.split("&").associate { field ->
            val (key, value) = field.split("=", limit = 2)
            URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private fun config(): ShowdownConfig =
        ShowdownConfig(
            loginUrl = loginServer.url(LOGIN_PATH).toString(),
            userAgent = USER_AGENT,
        )

    private companion object {
        const val LOGIN_PATH = "/api/login"
        const val USER_AGENT = "ThorShowdown/test"
        const val HTTP_SERVER_ERROR = 500
    }
}
