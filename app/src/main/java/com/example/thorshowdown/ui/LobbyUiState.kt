package com.example.thorshowdown.ui

import com.example.thorshowdown.data.BattleResult
import com.example.thorshowdown.data.SearchState
import com.example.thorshowdown.data.SessionsState

/** Bottom screen once logged in: the search control and the current battle, if any. */
data class LobbyUiState(
    val search: SearchState,
    val battle: BattleCard?,
) {
    companion object {
        /** The lobby for [search], showing the current battle from [sessions]. */
        fun from(
            search: SearchState,
            sessions: SessionsState,
        ): LobbyUiState {
            val current = sessions.currentRoomId?.let(sessions.sessions::get)
            return LobbyUiState(search, current?.let { BattleCard(it.roomId, it.title, it.result) })
        }
    }
}

/** Summary of the current battle until #7's battle controls replace it. */
data class BattleCard(
    val roomId: String,
    val title: String?,
    val result: BattleResult?,
)
