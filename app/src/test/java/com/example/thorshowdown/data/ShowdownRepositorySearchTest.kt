package com.example.thorshowdown.data

import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.fakes.FakeCredentialStore
import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.fakes.FakeShowdownClient.SearchCommand
import com.example.thorshowdown.showdown.NotConnectedException
import com.example.thorshowdown.showdown.RoomType
import com.example.thorshowdown.showdown.ShowdownEvent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class ShowdownRepositorySearchTest {
    private val client = FakeShowdownClient()

    private suspend fun TestScope.loggedInRepository(): ShowdownRepository =
        ShowdownRepository(client, LocalDataSource(FakeCredentialStore()), backgroundScope).also {
            it.login(FakeShowdownClient.USERNAME, PASSWORD)
        }

    @Test
    fun ShowdownRepository_StartSearchWhenLoggedIn_SendsNoTeamThenSearchAndIsRequested() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()

            repository.startSearch(FORMAT)

            assertEquals(listOf(SearchCommand.UseTeam(null), SearchCommand.Search(FORMAT_ID)), client.searchCommands)
            assertEquals(SearchState.Requested(FORMAT), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_ServerConfirmsSearch_IsSearching() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)

            client.emit(searchingFor(FORMAT_ID))

            assertEquals(SearchState.Searching(FORMAT), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_ServerFindsMatch_SearchIsIdleAndBattleIsCurrent() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)
            client.emit(searchingFor(FORMAT_ID))

            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))
            client.emit(ShowdownEvent.UpdateSearch(searching = emptyList(), games = mapOf(BATTLE_1 to BATTLE_TITLE)))

            assertEquals(SearchState.Idle(), repository.searchState.value)
            assertEquals(BATTLE_1, repository.sessions.value.currentRoomId)
        }

    @Test
    fun ShowdownRepository_StartSearchWhenLoggedOut_SendsNothingAndStaysIdle() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = ShowdownRepository(client, LocalDataSource(FakeCredentialStore()), backgroundScope)

            repository.startSearch(FORMAT)

            assertEquals(emptyList<SearchCommand>(), client.searchCommands)
            assertEquals(SearchState.Idle(), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_StartSearchWhileSearching_SendsOnlyOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)
            client.emit(searchingFor(FORMAT_ID))

            repository.startSearch(FORMAT)

            assertEquals(listOf(SearchCommand.UseTeam(null), SearchCommand.Search(FORMAT_ID)), client.searchCommands)
        }

    @Test
    fun ShowdownRepository_StartSearchWhileRequested_SendsOnlyOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)

            repository.startSearch(FORMAT)

            assertEquals(listOf(SearchCommand.UseTeam(null), SearchCommand.Search(FORMAT_ID)), client.searchCommands)
        }

    @Test
    fun ShowdownRepository_StartSearchDuringBattle_SearchesAndKeepsBattle() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            client.emit(ShowdownEvent.RoomInit(BATTLE_1, RoomType.BATTLE))

            repository.startSearch(FORMAT)

            assertEquals(SearchState.Requested(FORMAT), repository.searchState.value)
            assertEquals(BATTLE_1, repository.sessions.value.currentRoomId)
        }

    @Test
    fun ShowdownRepository_PopupWhileRequested_IsIdleWithRefusalAndLineBreaks() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)

            client.emit(ShowdownEvent.Popup(POPUP_MESSAGE))

            assertEquals(SearchState.Idle(SearchError.Refused(POPUP_MESSAGE_SHOWN)), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_SystemPmWhileRequested_IsIdleWithRefusal() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)

            client.emit(ShowdownEvent.PrivateMessage(ShowdownEvent.PrivateMessage.SYSTEM_SENDER, FakeShowdownClient.USERNAME, PM_MESSAGE))

            assertEquals(SearchState.Idle(SearchError.Refused(PM_MESSAGE)), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_PmFromPlayerWhileRequested_StaysRequested() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)

            client.emit(ShowdownEvent.PrivateMessage(OTHER_PLAYER, FakeShowdownClient.USERNAME, PM_MESSAGE))

            assertEquals(SearchState.Requested(FORMAT), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_PopupWhileSearching_IsIdleWithInterruption() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)
            client.emit(searchingFor(FORMAT_ID))

            client.emit(ShowdownEvent.Popup(OPPONENT_OFFLINE))

            assertEquals(SearchState.Idle(SearchError.Interrupted(OPPONENT_OFFLINE)), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_SystemPmWhileSearching_StaysSearching() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)
            client.emit(searchingFor(FORMAT_ID))

            client.emit(ShowdownEvent.PrivateMessage(ShowdownEvent.PrivateMessage.SYSTEM_SENDER, FakeShowdownClient.USERNAME, PM_MESSAGE))

            assertEquals(SearchState.Searching(FORMAT), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_PopupWhileIdle_StaysIdleWithoutError() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()

            client.emit(ShowdownEvent.Popup(POPUP_MESSAGE))

            assertEquals(SearchState.Idle(), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_SearchSendFails_IsIdleWithSendFailed() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            client.searchFailure = NotConnectedException()

            repository.startSearch(FORMAT)

            assertEquals(SearchState.Idle(SearchError.SendFailed), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_SearchSocketFails_IsIdleWithSendFailed() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            client.searchFailure = IOException(SOCKET_CLOSED)

            repository.startSearch(FORMAT)

            assertEquals(SearchState.Idle(SearchError.SendFailed), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_NoReplyWithinTimeout_IsIdleWithNoResponse() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)

            advanceTimeBy(SEARCH_TIMEOUT)
            runCurrent()

            assertEquals(SearchState.Idle(SearchError.NoResponse), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_ConfirmedBeforeTimeout_StaysSearching() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)
            client.emit(searchingFor(FORMAT_ID))

            advanceTimeBy(SEARCH_TIMEOUT)
            runCurrent()

            assertEquals(SearchState.Searching(FORMAT), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_RetryAfterRefusal_EarlierTimeoutDoesNotEndRetry() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)
            client.emit(ShowdownEvent.Popup(POPUP_MESSAGE))
            advanceTimeBy(SEARCH_TIMEOUT - ONE_SECOND)
            repository.startSearch(FORMAT)

            advanceTimeBy(ONE_SECOND * 2)
            runCurrent()

            assertEquals(SearchState.Requested(FORMAT), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_CancelWhileSearching_SendsCancelAndIsIdle() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)
            client.emit(searchingFor(FORMAT_ID))

            repository.cancelSearch()

            assertEquals(SearchCommand.CancelSearch, client.searchCommands.last())
            assertEquals(SearchState.Idle(), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_CancelWhileRequested_TimeoutDoesNotReportError() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)

            repository.cancelSearch()
            advanceTimeBy(SEARCH_TIMEOUT)
            runCurrent()

            assertEquals(SearchCommand.CancelSearch, client.searchCommands.last())
            assertEquals(SearchState.Idle(), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_CancelWhenIdle_SendsNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()

            repository.cancelSearch()

            assertEquals(emptyList<SearchCommand>(), client.searchCommands)
        }

    @Test
    fun ShowdownRepository_CancelSendFails_IsIdleWithSendFailed() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)
            client.emit(searchingFor(FORMAT_ID))
            client.searchFailure = IOException(SOCKET_CLOSED)

            repository.cancelSearch()

            assertEquals(SearchState.Idle(SearchError.SendFailed), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_DisconnectedWhileSearching_IsIdle() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)
            client.emit(searchingFor(FORMAT_ID))

            client.emit(ShowdownEvent.Disconnected(reason = null))

            assertEquals(SearchState.Idle(), repository.searchState.value)
        }

    @Test
    fun ShowdownRepository_DisconnectedWhileRequested_TimeoutDoesNotReportError() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = loggedInRepository()
            repository.startSearch(FORMAT)

            client.emit(ShowdownEvent.Disconnected(reason = null))
            advanceTimeBy(SEARCH_TIMEOUT)
            runCurrent()

            assertEquals(SearchState.Idle(), repository.searchState.value)
        }

    private fun searchingFor(formatId: String) = ShowdownEvent.UpdateSearch(searching = listOf(formatId), games = emptyMap())

    private companion object {
        const val BATTLE_1 = "battle-gen9randombattle-1"
        const val BATTLE_TITLE = "Alice vs. Bob"
        val FORMAT = BattleFormat.Gen9RandomBattle
        const val FORMAT_ID = "gen9randombattle"
        const val PASSWORD = "hunter2"
        const val POPUP_MESSAGE = "You must choose a name first.||Then search again."
        const val POPUP_MESSAGE_SHOWN = "You must choose a name first.\nThen search again."
        const val PM_MESSAGE = "Your team was rejected."
        const val OTHER_PLAYER = "Bob"
        const val OPPONENT_OFFLINE = "Sorry, your opponent guest8064286 went offline before your battle could start."
        const val SOCKET_CLOSED = "Socket closed"
        val SEARCH_TIMEOUT = 10.seconds
        val ONE_SECOND = 1.seconds
    }
}
