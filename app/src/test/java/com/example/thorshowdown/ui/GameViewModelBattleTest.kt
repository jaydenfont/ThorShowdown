package com.example.thorshowdown.ui

import com.example.thorshowdown.data.BattleResult
import com.example.thorshowdown.data.ShowdownRepository
import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.fakes.FakeCredentialStore
import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.fakes.FakeShowdownClient.RoomCommand
import com.example.thorshowdown.fakes.FakeWebSession
import com.example.thorshowdown.fakes.MainDispatcherRule
import com.example.thorshowdown.showdown.ActivePokemon
import com.example.thorshowdown.showdown.BattleRequest
import com.example.thorshowdown.showdown.Choice
import com.example.thorshowdown.showdown.MoveSlot
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

class GameViewModelBattleTest {
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
    fun GameViewModel_NoBattle_HasNoBattleMenu() {
        val viewModel = loggedInViewModel()

        assertNull(viewModel.bottomState.value.battle)
    }

    @Test
    fun GameViewModel_BattleStarts_BattleMenuShowsTitle() =
        runTest {
            val viewModel = loggedInViewModel()

            startBattle()

            assertEquals(
                BATTLE_TITLE,
                viewModel.bottomState.value.battle
                    ?.title,
            )
        }

    @Test
    fun GameViewModel_BattleWon_BattleMenuShowsResult() =
        runTest {
            val viewModel = loggedInViewModel()
            startBattle()

            client.emit(ShowdownEvent.Win(BATTLE_1, FakeShowdownClient.USERNAME))

            assertEquals(
                BattleResult.Won,
                viewModel.bottomState.value.battle
                    ?.result,
            )
        }

    @Test
    fun GameViewModel_ChooseMoveWithTera_SendsMoveToCurrentBattle() =
        runTest {
            val viewModel = loggedInViewModel()
            startBattle()

            viewModel.onAction(UserAction.ChooseMove(slot = 1, terastallize = true))

            assertEquals(listOf(RoomCommand.Choose(BATTLE_1, Choice.Move(1, terastallize = true), RQID)), client.roomCommands)
        }

    @Test
    fun GameViewModel_SwitchTo_SendsSwitchToCurrentBattle() =
        runTest {
            val viewModel = loggedInViewModel()
            startBattle()

            viewModel.onAction(UserAction.SwitchTo(slot = 2))

            assertEquals(listOf(RoomCommand.Choose(BATTLE_1, Choice.Switch(2), RQID)), client.roomCommands)
        }

    @Test
    fun GameViewModel_UndoAfterChoosing_SendsUndoAndMenuCanChooseAgain() =
        runTest {
            val viewModel = loggedInViewModel()
            startBattle()
            viewModel.onAction(UserAction.ChooseMove(slot = 1, terastallize = false))

            viewModel.onAction(UserAction.UndoChoice)

            assertEquals(RoomCommand.Choose(BATTLE_1, Choice.Undo, RQID), client.roomCommands.last())
            assertEquals(
                BattlePhase.Choose,
                viewModel.bottomState.value.battle
                    ?.phase,
            )
        }

    @Test
    fun GameViewModel_Forfeit_SendsForfeitOnly() =
        runTest {
            val viewModel = loggedInViewModel()
            startBattle()

            viewModel.onAction(UserAction.Forfeit)

            assertEquals(listOf(RoomCommand.Forfeit(BATTLE_1)), client.roomCommands)
        }

    @Test
    fun GameViewModel_StartTimer_SendsTimerOnToCurrentBattle() =
        runTest {
            val viewModel = loggedInViewModel()
            startBattle()

            viewModel.onAction(UserAction.StartTimer)

            assertEquals(listOf(RoomCommand.StartTimer(BATTLE_1)), client.roomCommands)
        }

    @Test
    fun GameViewModel_ChooseMoveWithoutBattle_SendsNothing() {
        val viewModel = loggedInViewModel()

        viewModel.onAction(UserAction.ChooseMove(slot = 1, terastallize = false))

        assertEquals(emptyList<RoomCommand>(), client.roomCommands)
    }

    private suspend fun startBattle() {
        client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))
        client.emit(ShowdownEvent.Title(BATTLE_1, BATTLE_TITLE))
        client.emit(ShowdownEvent.Request(BATTLE_1, request(), RAW_JSON))
    }

    private fun request() =
        BattleRequest(
            active = listOf(ActivePokemon(moves = listOf(MoveSlot(MOVE_NAME, MOVE_ID, pp = 8, maxPp = 8)))),
            side = Side(FakeShowdownClient.USERNAME, SIDE_ID),
            rqid = RQID,
        )

    private fun loggedInViewModel(): GameViewModel =
        GameViewModel(ShowdownRepository(client, LocalDataSource(FakeCredentialStore()), FakeWebSession(), eventScope))
            .also { it.onAction(UserAction.LogIn(FakeShowdownClient.USERNAME, PASSWORD)) }

    private companion object {
        const val PASSWORD = "hunter2"
        const val BATTLE_1 = "battle-gen9randombattle-1"
        const val BATTLE_TITLE = "Alice vs. Bob"
        const val SIDE_ID = "p1"
        const val RAW_JSON = "{}"
        const val RQID = 4
        const val MOVE_NAME = "Thunderbolt"
        const val MOVE_ID = "thunderbolt"
    }
}
