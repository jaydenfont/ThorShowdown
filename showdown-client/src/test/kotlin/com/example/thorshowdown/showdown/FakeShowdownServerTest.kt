package com.example.thorshowdown.showdown

import com.example.thorshowdown.showdown.fakes.FakeLoginApi
import com.example.thorshowdown.showdown.fakes.FakeShowdownServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** The fake server's own contract, used by the app's workflow tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeShowdownServerTest {
    private val server = FakeShowdownServer()

    @Test
    fun FakeShowdownServer_AutoLogin_ClientLogsInAsThatUser() =
        runTest(UnconfinedTestDispatcher()) {
            server.autoLogin(USERNAME)
            val client = newClient()
            client.connect()

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginResult.Success(USERNAME, FakeLoginApi.VALID_SID), result)
        }

    @Test
    fun FakeShowdownServer_AwaitSentAlreadySent_ReturnsFullMessage() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()
            client.choose(BATTLE_ROOM, Choice.Move(1), RQID)

            val message = server.awaitSent(CHOOSE_MOVE_1)

            assertEquals("$BATTLE_ROOM|$CHOOSE_MOVE_1|$RQID", message)
        }

    @Test
    fun FakeShowdownServer_AwaitSentBeforeSend_WaitsForIt() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()
            val awaited = async { server.awaitSent(CHOOSE_MOVE_1) }

            client.choose(BATTLE_ROOM, Choice.Move(1), RQID)

            assertEquals("$BATTLE_ROOM|$CHOOSE_MOVE_1|$RQID", awaited.await())
        }

    @Test
    fun FakeShowdownServer_SameCommandSentTwice_EachAwaitMatchesOneSend() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()
            client.choose(BATTLE_ROOM, Choice.Move(1), RQID)
            client.choose(BATTLE_ROOM, Choice.Undo, RQID)
            client.choose(BATTLE_ROOM, Choice.Move(1), NEXT_RQID)

            val first = server.awaitSent(CHOOSE_MOVE_1)
            val second = server.awaitSent(CHOOSE_MOVE_1)

            assertEquals(listOf("$BATTLE_ROOM|$CHOOSE_MOVE_1|$RQID", "$BATTLE_ROOM|$CHOOSE_MOVE_1|$NEXT_RQID"), listOf(first, second))
        }

    @Test
    fun FakeShowdownServer_AwaitSentNeverSent_FailsListingWhatWasSent() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()
            client.search(FORMAT)

            val failure = assertThrows(AssertionError::class.java) { runBlocking { server.awaitSent(CHOOSE_MOVE_1, SHORT_TIMEOUT) } }

            assertTrue(failure.message.orEmpty().contains("|/search $FORMAT"))
        }

    private fun TestScope.newClient(): ShowdownClient = DefaultShowdownClient(CONFIG, server, FakeLoginApi(), backgroundScope)

    private companion object {
        val CONFIG = ShowdownConfig(socketUrl = "wss://test.invalid/showdown/websocket")
        const val USERNAME = "qa-player"
        const val PASSWORD = "canary-password"
        const val BATTLE_ROOM = "battle-gen9randombattle-1"
        const val RQID = 3
        const val NEXT_RQID = 4
        const val CHOOSE_MOVE_1 = "/choose move 1"
        const val FORMAT = "gen9randombattle"
        val SHORT_TIMEOUT = 50.milliseconds
    }
}
