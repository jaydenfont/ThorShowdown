package com.example.thorshowdown.ui

import com.example.thorshowdown.data.ShowdownRepository
import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.fakes.FakeCredentialStore
import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.fakes.FakeWebSession
import com.example.thorshowdown.fakes.MainDispatcherRule
import com.example.thorshowdown.showdown.RoomType
import com.example.thorshowdown.showdown.ShowdownEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class GameViewModelTopScreenTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val client = FakeShowdownClient()

    /** Where the repository collects client events; cancelled after each test. */
    private val eventScope = CoroutineScope(UnconfinedTestDispatcher())

    @After
    fun cancelEventCollection() {
        eventScope.cancel()
    }

    @Test
    fun GameViewModel_LoggedInWithoutBattle_TopScreenIsIdle() {
        val viewModel = loggedInViewModel()

        assertNull(viewModel.topState.value.battleRoomId)
    }

    @Test
    fun GameViewModel_BattleStarts_TopScreenShowsIt() =
        runTest {
            val viewModel = loggedInViewModel()

            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

            assertEquals(BATTLE_1, viewModel.topState.value.battleRoomId)
        }

    @Test
    fun GameViewModel_PlayerSelectsOtherBattle_TopScreenFollows() =
        runTest {
            val viewModel = loggedInViewModel()
            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))
            client.emit(ShowdownEvent.RoomInit(BATTLE_2, RoomType.BATTLE))

            repository.selectRoom(BATTLE_1)

            assertEquals(BATTLE_1, viewModel.topState.value.battleRoomId)
        }

    @Test
    fun GameViewModel_BattleRoomCloses_TopScreenIsIdle() =
        runTest {
            val viewModel = loggedInViewModel()
            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

            client.emit(ShowdownEvent.RoomDeinit(BATTLE_1))

            assertNull(viewModel.topState.value.battleRoomId)
        }

    @Test
    fun GameViewModel_BattleRoomWhileLoggedOut_TopScreenShowsNothing() =
        runTest {
            val viewModel = GameViewModel(repository)

            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

            assertNull(viewModel.topState.value.battleRoomId)
        }

    private val repository = ShowdownRepository(client, LocalDataSource(FakeCredentialStore()), FakeWebSession(), eventScope)

    private fun loggedInViewModel(): GameViewModel =
        GameViewModel(repository).also { it.onAction(UserAction.LogIn(FakeShowdownClient.USERNAME, PASSWORD)) }

    private companion object {
        const val BATTLE_1 = "battle-gen9randombattle-1"
        const val BATTLE_2 = "battle-gen9randombattle-2"
        const val PASSWORD = "hunter2"
    }
}
