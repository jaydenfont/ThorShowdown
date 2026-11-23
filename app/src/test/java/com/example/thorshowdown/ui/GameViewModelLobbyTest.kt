package com.example.thorshowdown.ui

import com.example.thorshowdown.data.BattleFormat
import com.example.thorshowdown.data.BattleResult
import com.example.thorshowdown.data.SearchState
import com.example.thorshowdown.data.ShowdownRepository
import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.fakes.FakeCredentialStore
import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.fakes.FakeShowdownClient.RoomCommand
import com.example.thorshowdown.fakes.FakeShowdownClient.SearchCommand
import com.example.thorshowdown.fakes.FakeWebSession
import com.example.thorshowdown.fakes.MainDispatcherRule
import com.example.thorshowdown.showdown.BattleRequest
import com.example.thorshowdown.showdown.RoomType
import com.example.thorshowdown.showdown.ShowdownEvent
import com.example.thorshowdown.showdown.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class GameViewModelLobbyTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val client = FakeShowdownClient()

    /** Where the repository collects client events and runs timers; cancelled after each test. */
    private val eventScope = CoroutineScope(UnconfinedTestDispatcher())

    @After
    fun cancelEventCollection() {
        eventScope.cancel()
    }

    @Test
    fun GameViewModel_LoggedOut_HasNoLobby() {
        val viewModel = newViewModel()

        assertNull(viewModel.bottomState.value.lobby)
    }

    @Test
    fun GameViewModel_LoggedIn_ShowsIdleLobbyWithoutBattle() {
        val viewModel = loggedInViewModel()

        assertEquals(LobbyUiState(search = SearchState.Idle(), battle = null), viewModel.bottomState.value.lobby)
    }

    @Test
    fun GameViewModel_FindBattle_SearchesAndShowsRequested() {
        val viewModel = loggedInViewModel()

        viewModel.onAction(UserAction.FindBattle(FORMAT))

        assertEquals(SearchCommand.Search(FORMAT.id), client.searchCommands.last())
        assertEquals(
            SearchState.Requested(FORMAT),
            viewModel.bottomState.value.lobby
                ?.search,
        )
    }

    @Test
    fun GameViewModel_CancelSearch_SendsCancelAndShowsIdle() {
        val viewModel = loggedInViewModel()
        viewModel.onAction(UserAction.FindBattle(FORMAT))

        viewModel.onAction(UserAction.CancelSearch)

        assertEquals(SearchCommand.CancelSearch, client.searchCommands.last())
        assertEquals(
            SearchState.Idle(),
            viewModel.bottomState.value.lobby
                ?.search,
        )
    }

    @Test
    fun GameViewModel_BattleStarts_ShowsBattleCardWithTitle() =
        runTest {
            val viewModel = loggedInViewModel()

            startBattle()

            assertEquals(
                BattleCard(BATTLE_1, BATTLE_TITLE, result = null),
                viewModel.bottomState.value.lobby
                    ?.battle,
            )
        }

    @Test
    fun GameViewModel_BattleWon_BattleCardShowsResult() =
        runTest {
            val viewModel = loggedInViewModel()
            startBattle()
            client.emit(ShowdownEvent.Request(BATTLE_1, BattleRequest(side = Side(FakeShowdownClient.USERNAME, SIDE_ID)), RAW_JSON))

            client.emit(ShowdownEvent.Win(BATTLE_1, FakeShowdownClient.USERNAME))

            assertEquals(
                BattleResult.Won,
                viewModel.bottomState.value.lobby
                    ?.battle
                    ?.result,
            )
        }

    @Test
    fun GameViewModel_LeaveUnfinishedBattle_ForfeitsThenLeaves() =
        runTest {
            val viewModel = loggedInViewModel()
            startBattle()

            viewModel.onAction(UserAction.LeaveBattle(BATTLE_1))

            assertEquals(listOf(RoomCommand.Forfeit(BATTLE_1), RoomCommand.Leave(BATTLE_1)), client.roomCommands)
        }

    private suspend fun startBattle() {
        client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))
        client.emit(ShowdownEvent.Title(BATTLE_1, BATTLE_TITLE))
    }

    private fun newViewModel(): GameViewModel =
        GameViewModel(ShowdownRepository(client, LocalDataSource(FakeCredentialStore()), FakeWebSession(), eventScope))

    private fun loggedInViewModel(): GameViewModel =
        newViewModel().also { it.onAction(UserAction.LogIn(FakeShowdownClient.USERNAME, PASSWORD)) }

    private companion object {
        val FORMAT = BattleFormat.Gen9RandomBattle
        const val PASSWORD = "hunter2"
        const val BATTLE_1 = "battle-gen9randombattle-1"
        const val BATTLE_TITLE = "Alice vs. Bob"
        const val SIDE_ID = "p1"
        const val RAW_JSON = "{}"
    }
}
