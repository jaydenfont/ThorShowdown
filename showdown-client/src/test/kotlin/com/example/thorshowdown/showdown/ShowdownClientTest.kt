package com.example.thorshowdown.showdown

import com.example.thorshowdown.showdown.fakes.FakeLoginApi
import com.example.thorshowdown.showdown.fakes.FakeShowdownServer
import com.example.thorshowdown.showdown.fakes.LogCapture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ShowdownClientTest {
    private val server = FakeShowdownServer()

    @Test
    fun ShowdownClient_Connect_OpensConfiguredUrlAndEmitsConnected() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            val events = collectEvents(client)

            client.connect()

            assertEquals(CONFIG.socketUrl, server.openedUrl)
            assertEquals(listOf(ShowdownEvent.Connected), events)
        }

    @Test
    fun ShowdownClient_ServerSendsMessages_EmitsParsedEventsInOrder() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            val events = collectEvents(client)
            client.connect()

            server.push("|challstr|4|abc")
            server.push(">$BATTLE_ROOM\n|init|battle\n|turn|1")

            val expected =
                listOf(
                    ShowdownEvent.Connected,
                    ShowdownEvent.ChallStr("4|abc"),
                    ShowdownEvent.RoomInit(BATTLE_ROOM, RoomType.BATTLE),
                    ShowdownEvent.Turn(BATTLE_ROOM, 1),
                )
            assertEquals(expected, events)
        }

    @Test
    fun ShowdownClient_GlobalCommands_SendWithEmptyRoomPrefix() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()

            client.useTeam(packedTeam = null)
            client.useTeam(packedTeam = "Gengar||choicespecs|...")
            client.search("gen9randombattle")
            client.cancelSearch()
            client.join(BATTLE_ROOM)
            client.leave(BATTLE_ROOM)

            val expected =
                listOf(
                    "|/utm null",
                    "|/utm Gengar||choicespecs|...",
                    "|/search gen9randombattle",
                    "|/cancelsearch",
                    "|/join $BATTLE_ROOM",
                    "|/leave $BATTLE_ROOM",
                )
            assertEquals(expected, server.sent)
        }

    @Test
    fun ShowdownClient_BattleCommands_SendScopedToBattleRoom() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()

            client.choose(BATTLE_ROOM, Choice.Move(slot = 1, terastallize = true), rqid = 7)
            client.choose(BATTLE_ROOM, Choice.Move(slot = 4), rqid = 7)
            client.choose(BATTLE_ROOM, Choice.Switch(slot = 3), rqid = 8)
            client.choose(BATTLE_ROOM, Choice.Default, rqid = null)
            client.choose(BATTLE_ROOM, Choice.Undo, rqid = 8)
            client.forfeit(BATTLE_ROOM)

            val expected =
                listOf(
                    "$BATTLE_ROOM|/choose move 1 terastallize|7",
                    "$BATTLE_ROOM|/choose move 4|7",
                    "$BATTLE_ROOM|/choose switch 3|8",
                    "$BATTLE_ROOM|/choose default",
                    "$BATTLE_ROOM|/choose undo|8",
                    "$BATTLE_ROOM|/forfeit",
                )
            assertEquals(expected, server.sent)
        }

    @Test
    fun ShowdownClient_CommandBeforeConnect_ThrowsNotConnected() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()

            assertThrows(NotConnectedException::class.java) {
                runBlocking { client.search("gen9randombattle") }
            }
            assertEquals(emptyList<String>(), server.sent)
        }

    @Test
    fun ShowdownClient_Disconnect_EmitsDisconnectedAndRejectsCommands() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            val events = collectEvents(client)
            client.connect()

            client.disconnect()

            assertEquals(listOf(ShowdownEvent.Connected, ShowdownEvent.Disconnected(reason = null)), events)
            assertThrows(NotConnectedException::class.java) {
                runBlocking { client.search("gen9randombattle") }
            }
        }

    @Test
    fun ShowdownClient_ServerDropsConnection_EmitsDisconnectedWithReasonAndRejectsCommands() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            val events = collectEvents(client)
            client.connect()

            server.drop(reason = "server restart")

            assertEquals(listOf(ShowdownEvent.Connected, ShowdownEvent.Disconnected(reason = "server restart")), events)
            assertThrows(NotConnectedException::class.java) {
                runBlocking { client.search("gen9randombattle") }
            }
        }

    @Test
    fun ShowdownClient_LoginWithValidPassword_SendsTrnAndReturnsSuccess() =
        runTest(UnconfinedTestDispatcher()) {
            val loginApi = FakeLoginApi()
            val client = newClient(loginApi)
            client.connect()
            server.push("|challstr|$CHALLSTR")
            server.replyTo = { message -> UPDATE_USER_NAMED.takeIf { message.startsWith(TRN_PREFIX) } }

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginResult.Success(USERNAME), result)
            assertEquals(listOf(FakeLoginApi.Call(USERNAME, PASSWORD, CHALLSTR)), loginApi.calls)
            assertEquals(listOf("$TRN_PREFIX$USERNAME,0,${FakeLoginApi.VALID_ASSERTION}"), server.sent)
        }

    @Test
    fun ShowdownClient_LoginBeforeChallstrArrives_WaitsForChallstr() =
        runTest(UnconfinedTestDispatcher()) {
            val loginApi = FakeLoginApi()
            val client = newClient(loginApi)
            client.connect()
            server.replyTo = { message -> UPDATE_USER_NAMED.takeIf { message.startsWith(TRN_PREFIX) } }

            val result = async { client.login(USERNAME, PASSWORD) }
            server.push("|challstr|$CHALLSTR")

            assertEquals(LoginResult.Success(USERNAME), result.await())
            assertEquals(CHALLSTR, loginApi.calls.single().challstr)
        }

    @Test
    fun ShowdownClient_LoginRefusedWithEmptyAssertion_ReturnsWrongPasswordWithoutTrn() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient(FakeLoginApi(assertion = ""))
            client.connect()
            server.push("|challstr|$CHALLSTR")

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginFailure.WrongPassword, (result as LoginResult.Failure).reason)
            assertEquals(emptyList<String>(), server.sent)
        }

    @Test
    fun ShowdownClient_LoginRefusedWithErrorAssertion_ReturnsWrongPasswordWithoutTrn() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient(FakeLoginApi(assertion = ";;Wrong password."))
            client.connect()
            server.push("|challstr|$CHALLSTR")

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginFailure.WrongPassword, (result as LoginResult.Failure).reason)
            assertEquals(emptyList<String>(), server.sent)
        }

    @Test
    fun ShowdownClient_LoginWithNameTaken_ReturnsNameTaken() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()
            server.push("|challstr|$CHALLSTR")
            server.replyTo = { message -> "|nametaken|$USERNAME|That name is reserved.".takeIf { message.startsWith(TRN_PREFIX) } }

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginResult.Failure(LoginFailure.NameTaken, "That name is reserved."), result)
        }

    @Test
    fun ShowdownClient_LoginServerUnreachable_ReturnsNetworkFailure() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient(FakeLoginApi(failure = IOException("offline")))
            client.connect()
            server.push("|challstr|$CHALLSTR")

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginFailure.Network, (result as LoginResult.Failure).reason)
        }

    @Test
    fun ShowdownClient_LoginBeforeConnect_ReturnsNotConnected() =
        runTest(UnconfinedTestDispatcher()) {
            val loginApi = FakeLoginApi()
            val client = newClient(loginApi)

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginFailure.NotConnected, (result as LoginResult.Failure).reason)
            assertEquals(emptyList<FakeLoginApi.Call>(), loginApi.calls)
        }

    @Test
    fun ShowdownClient_ServerNeverConfirmsLogin_ReturnsTimeoutAfterConfiguredLimit() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()
            server.push("|challstr|$CHALLSTR")

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginFailure.Timeout, (result as LoginResult.Failure).reason)
            assertEquals(CONFIG.loginTimeout.inWholeMilliseconds, currentTime)
        }

    @Test
    fun ShowdownClient_ConnectionDropsDuringLogin_ReturnsNetworkFailureWithoutWaitingForTimeout() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()
            server.push("|challstr|$CHALLSTR")
            server.replyTo = { message -> null.also { if (message.startsWith(TRN_PREFIX)) server.drop("server restart") } }

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginFailure.Network, (result as LoginResult.Failure).reason)
            assertEquals(0L, currentTime)
        }

    @Test
    fun ShowdownClient_Login_LogsTrnWithAssertionRedactedAndNeverLogsSecrets() {
        val logs = LogCapture(LIBRARY_LOGGER)

        logs.use {
            runTest(UnconfinedTestDispatcher()) {
                val client = newClient()
                client.connect()
                server.push("|challstr|$CHALLSTR")
                server.replyTo = { message -> UPDATE_USER_NAMED.takeIf { message.startsWith(TRN_PREFIX) } }

                client.login(USERNAME, PASSWORD)
            }
        }

        assertTrue(logs.messages.any { "$TRN_PREFIX$USERNAME,0,<redacted>" in it })
        assertTrue(logs.messages.none { FakeLoginApi.VALID_ASSERTION in it || PASSWORD in it })
    }

    @Test
    fun ShowdownClient_SendFailsDuringLogin_ReturnsNetworkFailureInsteadOfThrowing() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            client.connect()
            server.push("|challstr|$CHALLSTR")
            server.failSends = true

            val result = client.login(USERNAME, PASSWORD)

            assertEquals(LoginFailure.Network, (result as LoginResult.Failure).reason)
        }

    @Test
    fun ShowdownClient_ConnectWhileConnected_OpensOnceAndDeliversEveryMessage() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            val events = collectEvents(client)
            client.connect()

            client.connect()
            server.push("|turn|1\n|turn|2")

            assertEquals(1, server.openCount)
            assertEquals(
                listOf(ShowdownEvent.Connected, ShowdownEvent.Turn(GLOBAL_ROOM_ID, 1), ShowdownEvent.Turn(GLOBAL_ROOM_ID, 2)),
                events,
            )
        }

    private fun TestScope.newClient(loginApi: LoginApi = FakeLoginApi()): ShowdownClient =
        DefaultShowdownClient(CONFIG, server, loginApi, backgroundScope)

    private fun TestScope.collectEvents(client: ShowdownClient): List<ShowdownEvent> {
        val events = mutableListOf<ShowdownEvent>()
        backgroundScope.launch { client.events.toList(events) }
        return events
    }

    private companion object {
        val CONFIG = ShowdownConfig(socketUrl = "wss://test.invalid/showdown/websocket")
        const val BATTLE_ROOM = "battle-gen9randombattle-123"
        const val CHALLSTR = "4|abc|def"
        const val USERNAME = "Alice"
        const val PASSWORD = "hunter2"
        const val TRN_PREFIX = "|/trn "
        const val UPDATE_USER_NAMED = "|updateuser| Alice|1|170|{}"
        const val LIBRARY_LOGGER = "com.example.thorshowdown.showdown"
    }
}
