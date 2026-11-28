package com.example.thorshowdown.data

import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.fakes.FakeCredentialStore
import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.fakes.FakeShowdownClient.RoomCommand
import com.example.thorshowdown.fakes.FakeWebSession
import com.example.thorshowdown.showdown.BattleRequest
import com.example.thorshowdown.showdown.Choice
import com.example.thorshowdown.showdown.RoomType
import com.example.thorshowdown.showdown.ShowdownEvent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownRepositorySessionTest {
    private val client = FakeShowdownClient()

    private fun TestScope.newRepository(): ShowdownRepository =
        ShowdownRepository(client, LocalDataSource(FakeCredentialStore()), FakeWebSession(), backgroundScope)

    @Test
    fun ShowdownRepository_ServerStartsBattle_SessionAppears() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = newRepository()

            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

            assertEquals(BATTLE_1, repository.sessions.value.currentRoomId)
        }

    @Test
    fun ShowdownRepository_Login_CollectsEventsBeforeConnecting() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = newRepository()

            repository.login(FakeShowdownClient.USERNAME, PASSWORD)

            assertEquals(1, client.subscribersAtConnect)
        }

    @Test
    fun ShowdownRepository_ChooseMove_SendsThroughSessionWithLatestRqid() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = newRepository()
            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))
            client.emit(ShowdownEvent.Request(BATTLE_1, BattleRequest(rqid = 5), RAW_JSON))

            repository.chooseMove(BATTLE_1, slot = 1, terastallize = false)

            assertEquals(listOf(RoomCommand.Choose(BATTLE_1, Choice.Move(1), 5)), client.roomCommands)
        }

    @Test
    fun ShowdownRepository_SwitchTo_SendsSwitchThroughSession() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = newRepository()
            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))
            client.emit(ShowdownEvent.Request(BATTLE_1, BattleRequest(rqid = 5), RAW_JSON))

            repository.switchTo(BATTLE_1, slot = 3)

            assertEquals(listOf(RoomCommand.Choose(BATTLE_1, Choice.Switch(3), 5)), client.roomCommands)
        }

    @Test
    fun ShowdownRepository_Forfeit_SendsForfeit() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = newRepository()
            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

            repository.forfeit(BATTLE_1)

            assertEquals(listOf(RoomCommand.Forfeit(BATTLE_1)), client.roomCommands)
        }

    @Test
    fun ShowdownRepository_LeaveThenServerDeinits_SessionIsRemoved() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = newRepository()
            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

            repository.leave(BATTLE_1)
            client.emit(ShowdownEvent.RoomDeinit(BATTLE_1))

            assertEquals(listOf(RoomCommand.Forfeit(BATTLE_1), RoomCommand.Leave(BATTLE_1)), client.roomCommands)
            assertEquals(SessionsState(), repository.sessions.value)
        }

    @Test
    fun ShowdownRepository_SelectRoom_ChangesCurrentRoom() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = newRepository()
            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))
            client.emit(ShowdownEvent.RoomInit(BATTLE_2, RoomType.BATTLE))

            repository.selectRoom(BATTLE_1)

            assertEquals(BATTLE_1, repository.sessions.value.currentRoomId)
        }

    private companion object {
        const val BATTLE_1 = "battle-gen9randombattle-1"
        const val BATTLE_2 = "battle-gen9randombattle-2"
        const val PASSWORD = "hunter2"
        const val RAW_JSON = "{}"
    }
}
