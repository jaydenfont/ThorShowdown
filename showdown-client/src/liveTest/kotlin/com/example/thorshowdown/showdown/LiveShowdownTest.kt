package com.example.thorshowdown.showdown

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Talks to the real Showdown server. Never searches the ladder, so it can't be matched
 * against a real player. Run with `.\gradlew.bat :showdown-client:liveTest`.
 */
class LiveShowdownTest {
    private val config = ShowdownConfig()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = DefaultShowdownClient(config, OkHttpTransport(config), OkHttpLoginApi(config), scope)

    @After
    fun tearDown() {
        runBlocking { client.disconnect() }
        scope.cancel()
    }

    @Test
    fun LiveShowdown_ConnectAndLoginWithUnregisteredName_ReceivesChallstrAndLogsIn() =
        runBlocking {
            withTimeout(LIVE_TIMEOUT) {
                val challstr =
                    client.events
                        .onSubscription { client.connect() }
                        .filterIsInstance<ShowdownEvent.ChallStr>()
                        .first()

                val result = client.login(throwawayName(), THROWAWAY_PASSWORD)

                assertTrue(challstr.challstr.isNotBlank())
                assertTrue("Login failed: $result", result is LoginResult.Success)
            }
        }

    @Test
    fun LiveShowdown_CancelSearchWhileIdle_ServerRepliesWithEmptySearch() =
        runBlocking {
            withTimeout(LIVE_TIMEOUT) {
                client.events
                    .onSubscription { client.connect() }
                    .filterIsInstance<ShowdownEvent.ChallStr>()
                    .first()

                val update =
                    client.events
                        .onSubscription { client.cancelSearch() }
                        .filterIsInstance<ShowdownEvent.UpdateSearch>()
                        .first()

                assertEquals(emptyList<String>(), update.searching)
            }
        }

    /** Unregistered names accept any password, so each run claims a fresh one. */
    private fun throwawayName(): String = THROWAWAY_PREFIX + Random.nextInt(THROWAWAY_SUFFIX_RANGE)

    private companion object {
        val LIVE_TIMEOUT = 30.seconds
        const val THROWAWAY_PREFIX = "thortest"
        const val THROWAWAY_PASSWORD = "thorshowdown-live-test"
        const val THROWAWAY_SUFFIX_RANGE = 1_000_000
    }
}
