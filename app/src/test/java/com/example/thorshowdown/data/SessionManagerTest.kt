package com.example.thorshowdown.data

import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.fakes.FakeShowdownClient.RoomCommand
import com.example.thorshowdown.showdown.BattleRequest
import com.example.thorshowdown.showdown.Choice
import com.example.thorshowdown.showdown.NotConnectedException
import com.example.thorshowdown.showdown.RoomType
import com.example.thorshowdown.showdown.ShowdownEvent
import com.example.thorshowdown.showdown.Side
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SessionManagerTest {
    private val client = FakeShowdownClient()
    private val sessions = SessionManager(client)

    @Test
    fun SessionManager_BattleRoomInit_CreatesSessionAndMakesItCurrent() {
        sessions.handle(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

        val state = sessions.state.value

        assertEquals(mapOf(BATTLE_1 to BattleSession(BATTLE_1)), state.sessions)
        assertEquals(BATTLE_1, state.currentRoomId)
    }

    @Test
    fun SessionManager_ChatRoomInit_CreatesNoSession() {
        sessions.handle(ShowdownEvent.RoomInit(LOBBY, RoomType.CHAT))

        assertEquals(SessionsState(), sessions.state.value)
    }

    @Test
    fun SessionManager_SecondBattleStarts_BecomesCurrentAndKeepsFirst() {
        sessions.handle(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

        sessions.handle(ShowdownEvent.RoomInit(BATTLE_2, RoomType.BATTLE))

        assertEquals(
            listOf(BATTLE_1, BATTLE_2),
            sessions.state.value.sessions.keys
                .toList(),
        )
        assertEquals(BATTLE_2, sessions.state.value.currentRoomId)
    }

    @Test
    fun SessionManager_Title_SetsSessionTitle() {
        sessions.handle(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

        sessions.handle(ShowdownEvent.Title(BATTLE_1, TITLE))

        assertEquals(
            TITLE,
            sessions.state.value.sessions
                .getValue(BATTLE_1)
                .title,
        )
    }

    @Test
    fun SessionManager_EventForUnknownRoom_IsIgnored() {
        sessions.handle(ShowdownEvent.Title(BATTLE_1, TITLE))

        assertEquals(SessionsState(), sessions.state.value)
    }

    @Test
    fun SessionManager_CurrentRoomDeinit_RemovesSessionAndFallsBackToMostRecent() {
        startBattles(BATTLE_1, BATTLE_2, BATTLE_3)

        sessions.handle(ShowdownEvent.RoomDeinit(BATTLE_3))

        assertEquals(
            listOf(BATTLE_1, BATTLE_2),
            sessions.state.value.sessions.keys
                .toList(),
        )
        assertEquals(BATTLE_2, sessions.state.value.currentRoomId)
    }

    @Test
    fun SessionManager_OtherRoomDeinit_KeepsCurrentRoom() {
        startBattles(BATTLE_1, BATTLE_2)

        sessions.handle(ShowdownEvent.RoomDeinit(BATTLE_1))

        assertEquals(BATTLE_2, sessions.state.value.currentRoomId)
    }

    @Test
    fun SessionManager_LastRoomDeinit_HasNoCurrentRoom() {
        startBattles(BATTLE_1)

        sessions.handle(ShowdownEvent.RoomDeinit(BATTLE_1))

        assertEquals(emptyMap<String, BattleSession>(), sessions.state.value.sessions)
        assertNull(sessions.state.value.currentRoomId)
    }

    @Test
    fun SessionManager_Request_StoresRequestAndAwaitsChoice() {
        startBattles(BATTLE_1)

        sessions.handle(requestEvent(BATTLE_1, rqid = 3))

        val session =
            sessions.state.value.sessions
                .getValue(BATTLE_1)
        assertEquals(3, session.request?.rqid)
        assertFalse(session.waitingForOpponent)
        assertTrue(session.awaitingChoice)
    }

    @Test
    fun SessionManager_WaitRequest_IsWaitingWithoutChoice() {
        startBattles(BATTLE_1)

        sessions.handle(requestEvent(BATTLE_1, rqid = 3, wait = true))

        val session =
            sessions.state.value.sessions
                .getValue(BATTLE_1)
        assertTrue(session.waitingForOpponent)
        assertFalse(session.awaitingChoice)
    }

    @Test
    fun SessionManager_Turn_SetsTurnNumber() {
        startBattles(BATTLE_1)

        sessions.handle(ShowdownEvent.Turn(BATTLE_1, 7))

        assertEquals(
            7,
            sessions.state.value.sessions
                .getValue(BATTLE_1)
                .turn,
        )
    }

    @Test
    fun SessionManager_BattleError_SetsErrorAndAllowsChoosingAgain() {
        startBattles(BATTLE_1)
        sessions.handle(requestEvent(BATTLE_1, rqid = 3, wait = true))

        sessions.handle(ShowdownEvent.BattleError(BATTLE_1, INVALID_CHOICE))

        val session =
            sessions.state.value.sessions
                .getValue(BATTLE_1)
        assertEquals(INVALID_CHOICE, session.lastError)
        assertFalse(session.waitingForOpponent)
    }

    @Test
    fun SessionManager_NewRequestAfterError_ClearsError() {
        startBattles(BATTLE_1)
        sessions.handle(ShowdownEvent.BattleError(BATTLE_1, INVALID_CHOICE))

        sessions.handle(requestEvent(BATTLE_1, rqid = 4))

        assertNull(
            sessions.state.value.sessions
                .getValue(BATTLE_1)
                .lastError,
        )
    }

    @Test
    fun SessionManager_WeWin_ResultIsWonAndSessionIsKept() {
        startBattles(BATTLE_1)
        sessions.handle(requestEvent(BATTLE_1, rqid = 3))

        sessions.handle(ShowdownEvent.Win(BATTLE_1, OUR_DISPLAY_NAME))

        val session =
            sessions.state.value.sessions
                .getValue(BATTLE_1)
        assertEquals(BattleResult.Won, session.result)
        assertFalse(session.awaitingChoice)
        assertEquals(BATTLE_1, sessions.state.value.currentRoomId)
    }

    @Test
    fun SessionManager_OpponentWins_ResultIsLost() {
        startBattles(BATTLE_1)
        sessions.handle(requestEvent(BATTLE_1, rqid = 3))

        sessions.handle(ShowdownEvent.Win(BATTLE_1, OPPONENT))

        assertEquals(
            BattleResult.Lost,
            sessions.state.value.sessions
                .getValue(BATTLE_1)
                .result,
        )
    }

    @Test
    fun SessionManager_Tie_ResultIsTied() {
        startBattles(BATTLE_1)

        sessions.handle(ShowdownEvent.Tie(BATTLE_1))

        assertEquals(
            BattleResult.Tied,
            sessions.state.value.sessions
                .getValue(BATTLE_1)
                .result,
        )
    }

    @Test
    fun SessionManager_ChooseMoveWhenAwaitingChoice_SendsMoveWithRqidAndWaits() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 3))

            sessions.chooseMove(BATTLE_1, slot = 2, terastallize = true)

            assertEquals(listOf(RoomCommand.Choose(BATTLE_1, Choice.Move(2, terastallize = true), 3)), client.roomCommands)
            assertTrue(
                sessions.state.value.sessions
                    .getValue(BATTLE_1)
                    .waitingForOpponent,
            )
        }

    @Test
    fun SessionManager_SwitchToWhenAwaitingChoice_SendsSwitchWithRqid() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 3))

            sessions.switchTo(BATTLE_1, slot = 4)

            assertEquals(listOf(RoomCommand.Choose(BATTLE_1, Choice.Switch(4), 3)), client.roomCommands)
        }

    @Test
    fun SessionManager_ChooseTwiceForOneRequest_SendsOnlyFirstChoice() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 3))
            sessions.chooseMove(BATTLE_1, slot = 1, terastallize = false)

            sessions.chooseMove(BATTLE_1, slot = 2, terastallize = false)

            assertEquals(1, client.roomCommands.size)
        }

    @Test
    fun SessionManager_ChooseWithoutRequest_SendsNothing() =
        runTest {
            startBattles(BATTLE_1)

            sessions.chooseMove(BATTLE_1, slot = 1, terastallize = false)

            assertEquals(emptyList<RoomCommand>(), client.roomCommands)
        }

    @Test
    fun SessionManager_ChooseInUnknownRoom_SendsNothing() =
        runTest {
            sessions.switchTo(BATTLE_1, slot = 2)

            assertEquals(emptyList<RoomCommand>(), client.roomCommands)
        }

    @Test
    fun SessionManager_ChooseAfterBattleEnded_SendsNothing() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 3))
            sessions.handle(ShowdownEvent.Win(BATTLE_1, OPPONENT))

            sessions.chooseMove(BATTLE_1, slot = 1, terastallize = false)

            assertEquals(emptyList<RoomCommand>(), client.roomCommands)
        }

    @Test
    fun SessionManager_ChooseWhenSendFails_RecordsErrorAndStaysAbleToChoose() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 3))
            client.commandFailure = NotConnectedException()

            sessions.chooseMove(BATTLE_1, slot = 1, terastallize = false)

            val session =
                sessions.state.value.sessions
                    .getValue(BATTLE_1)
            assertNotNull(session.lastError)
            assertTrue(session.awaitingChoice)
        }

    @Test
    fun SessionManager_Forfeit_SendsForfeitAndKeepsSession() =
        runTest {
            startBattles(BATTLE_1)

            sessions.forfeit(BATTLE_1)

            assertEquals(listOf(RoomCommand.Forfeit(BATTLE_1)), client.roomCommands)
            assertTrue(BATTLE_1 in sessions.state.value.sessions)
        }

    @Test
    fun SessionManager_LeaveUnfinishedBattle_ForfeitsThenLeavesAndWaitsForDeinit() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 3))

            sessions.leave(BATTLE_1)

            assertEquals(listOf(RoomCommand.Forfeit(BATTLE_1), RoomCommand.Leave(BATTLE_1)), client.roomCommands)
            assertTrue(BATTLE_1 in sessions.state.value.sessions)
        }

    @Test
    fun SessionManager_LeaveFinishedBattle_LeavesWithoutForfeiting() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(ShowdownEvent.Tie(BATTLE_1))

            sessions.leave(BATTLE_1)

            assertEquals(listOf(RoomCommand.Leave(BATTLE_1)), client.roomCommands)
        }

    @Test
    fun SessionManager_LeaveWhenSendFails_RecordsErrorWithoutThrowing() =
        runTest {
            startBattles(BATTLE_1)
            client.commandFailure = IOException(SOCKET_CLOSED)

            sessions.leave(BATTLE_1)

            assertNotNull(
                sessions.state.value.sessions
                    .getValue(BATTLE_1)
                    .lastError,
            )
        }

    @Test
    fun SessionManager_SelectRoom_SwitchesCurrentWithoutSendingAnything() {
        startBattles(BATTLE_1, BATTLE_2)

        sessions.selectRoom(BATTLE_1)

        assertEquals(BATTLE_1, sessions.state.value.currentRoomId)
        assertEquals(emptyList<RoomCommand>(), client.roomCommands)
        assertEquals(setOf(BATTLE_1, BATTLE_2), sessions.state.value.sessions.keys)
    }

    @Test
    fun SessionManager_SelectUnknownRoom_KeepsCurrentRoom() {
        startBattles(BATTLE_1)

        sessions.selectRoom(BATTLE_2)

        assertEquals(BATTLE_1, sessions.state.value.currentRoomId)
    }

    @Test
    fun SessionManager_UndoAfterChoosing_SendsUndoWithRqidAndAwaitsChoiceAgain() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 7))
            sessions.chooseMove(BATTLE_1, slot = 1, terastallize = false)

            sessions.undo(BATTLE_1)

            assertEquals(RoomCommand.Choose(BATTLE_1, Choice.Undo, 7), client.roomCommands.last())
            assertTrue(
                sessions.state.value.sessions
                    .getValue(BATTLE_1)
                    .awaitingChoice,
            )
        }

    @Test
    fun SessionManager_UndoThenChooseAgain_SendsNewChoice() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 7))
            sessions.chooseMove(BATTLE_1, slot = 1, terastallize = false)
            sessions.undo(BATTLE_1)

            sessions.switchTo(BATTLE_1, slot = 3)

            assertEquals(RoomCommand.Choose(BATTLE_1, Choice.Switch(3), 7), client.roomCommands.last())
        }

    @Test
    fun SessionManager_UndoWithoutChoice_SendsNothing() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 7))

            sessions.undo(BATTLE_1)

            assertEquals(emptyList<RoomCommand>(), client.roomCommands)
        }

    @Test
    fun SessionManager_UndoWhileServerSaysWait_SendsNothing() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 7, wait = true))

            sessions.undo(BATTLE_1)

            assertEquals(emptyList<RoomCommand>(), client.roomCommands)
        }

    @Test
    fun SessionManager_UndoAfterBattleEnded_SendsNothing() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 7))
            sessions.chooseMove(BATTLE_1, slot = 1, terastallize = false)
            sessions.handle(ShowdownEvent.Win(BATTLE_1, OPPONENT))
            val sentBefore = client.roomCommands.size

            sessions.undo(BATTLE_1)

            assertEquals(sentBefore, client.roomCommands.size)
        }

    @Test
    fun SessionManager_UndoSendFails_StaysWaitingAndShowsError() =
        runTest {
            startBattles(BATTLE_1)
            sessions.handle(requestEvent(BATTLE_1, rqid = 7))
            sessions.chooseMove(BATTLE_1, slot = 1, terastallize = false)
            client.commandFailure = IOException(SOCKET_CLOSED)

            sessions.undo(BATTLE_1)

            val session =
                sessions.state.value.sessions
                    .getValue(BATTLE_1)
            assertTrue(session.waitingForOpponent)
            assertNotNull(session.lastError)
        }

    private fun requestEvent(
        roomId: String,
        rqid: Int,
        wait: Boolean = false,
    ): ShowdownEvent.Request =
        ShowdownEvent.Request(
            roomId,
            BattleRequest(wait = wait, side = Side(OUR_SIDE_NAME, SIDE_ID), rqid = rqid),
            rawJson = RAW_JSON,
        )

    private fun startBattles(vararg roomIds: String) {
        roomIds.forEach { sessions.handle(ShowdownEvent.RoomInit(it, RoomType.BATTLE)) }
    }

    private companion object {
        const val BATTLE_1 = "battle-gen9randombattle-1"
        const val BATTLE_2 = "battle-gen9randombattle-2"
        const val BATTLE_3 = "battle-gen9randombattle-3"
        const val LOBBY = "lobby"
        const val TITLE = "Alice vs. Bob"
        const val OUR_SIDE_NAME = "alice"
        const val OUR_DISPLAY_NAME = "Alice"
        const val OPPONENT = "Bob"
        const val SIDE_ID = "p1"
        const val RAW_JSON = "{}"
        const val SOCKET_CLOSED = "socket closed"
        const val INVALID_CHOICE = "[Invalid choice] Can't move: Gengar's Shadow Ball is disabled"
    }
}
