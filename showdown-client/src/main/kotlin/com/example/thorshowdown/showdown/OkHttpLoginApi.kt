package com.example.thorshowdown.showdown

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [LoginApi] that posts to Showdown's `/api/login` endpoint with OkHttp. */
public class OkHttpLoginApi(
    private val config: ShowdownConfig,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : LoginApi {
    override suspend fun fetchAssertion(
        name: String,
        password: String,
        challstr: String,
    ): LoginResponse {
        val reply = post(loginRequest(name, password, challstr))
        return LoginResponse(parseAssertion(reply.body), findSid(reply.setCookies))
    }

    private fun loginRequest(
        name: String,
        password: String,
        challstr: String,
    ): Request {
        val form =
            FormBody
                .Builder()
                .add(FIELD_NAME, name)
                .add(FIELD_PASSWORD, password)
                .add(FIELD_CHALLSTR, challstr)
                .build()
        return Request
            .Builder()
            .url(config.loginUrl)
            .header(HEADER_USER_AGENT, config.userAgent)
            .post(form)
            .build()
    }

    /** Sends [request] and returns the body and `Set-Cookie` headers of a successful response. */
    private suspend fun post(request: Request): Reply =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        response.use {
                            if (it.isSuccessful) {
                                continuation.resume(Reply(it.body.string(), it.headers(HEADER_SET_COOKIE)))
                            } else {
                                continuation.resumeWithException(IOException("$HTTP_ERROR_MESSAGE ${it.code}"))
                            }
                        }
                    }
                },
            )
        }

    /**
     * The body is JSON prefixed with `]` to stop it being run as a script.
     * A missing assertion means the login was refused; an unreadable body is an error.
     */
    private fun parseAssertion(body: String): String {
        val json = body.removePrefix(ANTI_HIJACK_PREFIX)
        val response =
            ProtocolJson.decodeOrNull(LoginBody.serializer(), json)
                ?: throw IOException(UNREADABLE_RESPONSE_MESSAGE)
        return response.assertion.orEmpty()
    }

    /**
     * The `sid` value from [setCookies], as sent. Only the leading `name=value` of each header
     * matters; the attributes (domain, expiry) are for the browser that will receive it.
     */
    private fun findSid(setCookies: List<String>): String? =
        setCookies
            .map { it.substringBefore(COOKIE_ATTRIBUTE_SEPARATOR) }
            .firstOrNull { it.substringBefore(COOKIE_NAME_SEPARATOR).trim() == SID_COOKIE_NAME }
            ?.substringAfter(COOKIE_NAME_SEPARATOR)

    private class Reply(
        val body: String,
        val setCookies: List<String>,
    )

    @Serializable
    private data class LoginBody(
        val assertion: String? = null,
    )

    private companion object {
        const val HEADER_SET_COOKIE = "Set-Cookie"
        const val SID_COOKIE_NAME = "sid"
        const val COOKIE_ATTRIBUTE_SEPARATOR = ";"
        const val COOKIE_NAME_SEPARATOR = "="
        const val FIELD_NAME = "name"
        const val FIELD_PASSWORD = "pass"
        const val FIELD_CHALLSTR = "challstr"
        const val HEADER_USER_AGENT = "User-Agent"
        const val ANTI_HIJACK_PREFIX = "]"
        const val HTTP_ERROR_MESSAGE = "Login server returned HTTP"
        const val UNREADABLE_RESPONSE_MESSAGE = "Login server response wasn't the expected JSON"
    }
}
