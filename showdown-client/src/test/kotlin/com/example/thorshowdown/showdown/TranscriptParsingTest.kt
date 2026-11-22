package com.example.thorshowdown.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses a real gen9 random battle transcript (see `src/test/resources/battles/README.md`). */
class TranscriptParsingTest {
    private val events = ProtocolParser.parse(readResource(TRANSCRIPT))

    @Test
    fun ProtocolParser_FullBattleTranscript_ReturnsOneEventPerLine() {
        assertEquals(NON_EMPTY_LINES - ROOM_MARKERS, events.size)
    }

    @Test
    fun ProtocolParser_FullBattleTranscript_AssignsEveryEventToTheBattleRoom() {
        assertTrue(events.all { it.roomId == BATTLE_ROOM })
    }

    @Test
    fun ProtocolParser_FullBattleTranscript_ReturnsTurnsOneToSix() {
        val turns = events.filterIsInstance<ShowdownEvent.Turn>().map { it.number }

        assertEquals((1..6).toList(), turns)
    }

    @Test
    fun ProtocolParser_FullBattleTranscript_ReturnsRequestsWithRqidsOneToSix() {
        val rqids = events.filterIsInstance<ShowdownEvent.Request>().map { it.request.rqid }

        assertEquals((1..6).toList(), rqids)
    }

    @Test
    fun ProtocolParser_FullBattleTranscript_FirstMoveRequestOffersGhostTera() {
        val firstMoveRequest = events.filterIsInstance<ShowdownEvent.Request>().first { it.request.active.isNotEmpty() }

        assertEquals(
            "Ghost",
            firstMoveRequest.request.active
                .single()
                .canTerastallize,
        )
    }

    @Test
    fun ProtocolParser_FullBattleTranscript_EndsWithWin() {
        assertEquals(ShowdownEvent.Win(BATTLE_ROOM, "coffee_enjoyer"), events.last())
    }

    private fun readResource(path: String): String =
        checkNotNull(javaClass.classLoader.getResource(path)) { "Missing test resource $path" }.readText()

    private companion object {
        const val TRANSCRIPT = "battles/gen9-singles.txt"
        const val BATTLE_ROOM = "battle-gen9randombattle-2043118842"

        /** Counted from the transcript file with `grep -c .` and `grep -c '^>'`. */
        const val NON_EMPTY_LINES = 83
        const val ROOM_MARKERS = 1
    }
}
