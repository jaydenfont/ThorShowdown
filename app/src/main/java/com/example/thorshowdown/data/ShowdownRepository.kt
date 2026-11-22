package com.example.thorshowdown.data

import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.showdown.ShowdownClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Placeholder for parsed battle state. */
data class BattleState(
    val log: List<String> = emptyList(),
)

/** App-scoped single source of truth. Combines the Showdown client and local data. */
class ShowdownRepository(
    private val client: ShowdownClient,
    private val local: LocalDataSource,
) {
    private val _battleState = MutableStateFlow(BattleState())
    val battleState: StateFlow<BattleState> = _battleState.asStateFlow()

    /** Searches the ladder for [formatId] without a team (random formats). */
    suspend fun startSearch(formatId: String) {
        client.useTeam(packedTeam = null)
        client.search(formatId)
    }
}
