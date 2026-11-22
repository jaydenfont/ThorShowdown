package com.example.thorshowdown.data

import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.showdown.ProtocolParser
import com.example.thorshowdown.showdown.RoomType
import com.example.thorshowdown.showdown.ShowdownEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays a real battle transcript (see resources/battles/README.md) through the parser and SessionManager. */
class SessionTranscriptTest {
    private val sessions = SessionManager(FakeShowdownClient())

    @Test
    fun SessionManager_ReplayGen9SinglesWin_EndsOnFinalTurnAsWon() {
        // The capture starts after |init|battle, so start the session the way that line would.
        sessions.handle(ShowdownEvent.RoomInit(BATTLE_ROOM, RoomType.BATTLE))

        ProtocolParser.parse(transcript()).forEach(sessions::handle)

        val state = sessions.state.value
        val session = state.sessions.getValue(BATTLE_ROOM)
        assertEquals(setOf(BATTLE_ROOM), state.sessions.keys)
        assertEquals(BATTLE_ROOM, state.currentRoomId)
        assertEquals(FINAL_TURN, session.turn)
        assertEquals(BattleResult.Won, session.result)
        assertEquals(PLAYER, session.request?.side?.name)
        assertFalse(session.awaitingChoice)
    }

    @Test
    fun SessionManager_ReplayUpToFirstTurnRequest_AwaitsChoiceDespiteTurnLogFollowing() {
        sessions.handle(ShowdownEvent.RoomInit(BATTLE_ROOM, RoomType.BATTLE))
        val upToSecondTurnLog = transcript().lineSequence().takeWhile { it != SECOND_TURN_LINE }.joinToString(LINE_BREAK)

        ProtocolParser.parse(upToSecondTurnLog).forEach(sessions::handle)

        val session =
            sessions.state.value.sessions
                .getValue(BATTLE_ROOM)
        assertEquals(1, session.turn)
        assertTrue(session.awaitingChoice)
    }

    private fun transcript(): String = checkNotNull(javaClass.getResource(TRANSCRIPT_PATH)) { "Missing $TRANSCRIPT_PATH" }.readText()

    private companion object {
        const val TRANSCRIPT_PATH = "/battles/gen9-singles.txt"
        const val BATTLE_ROOM = "battle-gen9randombattle-2043118842"
        const val PLAYER = "coffee_enjoyer"
        const val FINAL_TURN = 6
        const val SECOND_TURN_LINE = "|turn|2"
        const val LINE_BREAK = "\n"
    }
}
