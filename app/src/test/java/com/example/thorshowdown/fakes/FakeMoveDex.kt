package com.example.thorshowdown.fakes

import com.example.thorshowdown.data.moves.MoveDex
import com.example.thorshowdown.data.moves.MoveInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [MoveDex] holding exactly [moves], already loaded. */
class FakeMoveDex(
    moves: Map<String, MoveInfo> = emptyMap(),
) : MoveDex {
    override val moves: StateFlow<Map<String, MoveInfo>> = MutableStateFlow(moves)
}
