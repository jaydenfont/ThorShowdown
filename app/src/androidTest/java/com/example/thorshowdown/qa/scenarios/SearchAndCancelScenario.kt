package com.example.thorshowdown.qa.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.thorshowdown.qa.QaRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Search for a Random Battle, then cancel before a match. Spec: qa/scenarios/SearchAndCancel.md. */
@RunWith(AndroidJUnit4::class)
class SearchAndCancelScenario {
    @get:Rule val qa = QaRule()

    @Test
    fun SearchAndCancel_FindThenCancel_SendsBothAndReturnsToIdle() =
        runBlocking {
            qa.logInToLobby()

            qa.lobby.findBattle()
            qa.server.awaitSent(SEARCH)
            qa.server.push(SEARCHING)
            qa.lobby.assertSearching()
            qa.checkpoint(SearchAndCancelCheckpoint.Searching)

            qa.lobby.cancelSearch()
            qa.server.awaitSent(CANCEL)
            qa.server.push(NOT_SEARCHING)
            qa.lobby.assertIdle()
            qa.checkpoint(SearchAndCancelCheckpoint.Cancelled)
        }

    private companion object {
        const val SEARCH = "/search gen9randombattle"
        const val CANCEL = "/cancelsearch"
        const val SEARCHING = "|updatesearch|{\"searching\":[\"gen9randombattle\"],\"games\":null}"
        const val NOT_SEARCHING = "|updatesearch|{\"searching\":[],\"games\":null}"
    }
}

/** Screenshot points; each has a heading in qa/scenarios/SearchAndCancel.md. */
enum class SearchAndCancelCheckpoint { Searching, Cancelled }
