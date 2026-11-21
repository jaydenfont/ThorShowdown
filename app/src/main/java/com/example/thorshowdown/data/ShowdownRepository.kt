package com.example.thorshowdown.data

import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.data.remote.ShowdownDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Placeholder for parsed battle state. */
data class BattleState(val log: List<String> = emptyList())

/** App-scoped single source of truth. Combines remote and local data. */
class ShowdownRepository(
    private val remote: ShowdownDataSource,
    private val local: LocalDataSource,
) {
    private val _battleState = MutableStateFlow(BattleState())
    val battleState: StateFlow<BattleState> = _battleState.asStateFlow()

    suspend fun sendCommand(command: String) {
        remote.send(command)
    }
}
