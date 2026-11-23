package com.example.thorshowdown.ui

import com.example.thorshowdown.data.BattleResult
import com.example.thorshowdown.data.BattleSession
import com.example.thorshowdown.data.SessionManager
import com.example.thorshowdown.fakes.FakeShowdownClient
import com.example.thorshowdown.showdown.ActivePokemon
import com.example.thorshowdown.showdown.BattleRequest
import com.example.thorshowdown.showdown.Gender
import com.example.thorshowdown.showdown.MoveDisabled
import com.example.thorshowdown.showdown.MoveSlot
import com.example.thorshowdown.showdown.ProtocolParser
import com.example.thorshowdown.showdown.RoomType
import com.example.thorshowdown.showdown.ShowdownEvent
import com.example.thorshowdown.showdown.Side
import com.example.thorshowdown.showdown.SidePokemon
import com.example.thorshowdown.showdown.Stats
import com.example.thorshowdown.showdown.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleUiStateTest {
    @Test
    fun BattleUiState_NoRequestYet_IsWaitingWithoutMoves() {
        val state = BattleUiState.from(BattleSession(ROOM, title = TITLE))

        assertEquals(BattlePhase.Waiting, state.phase)
        assertEquals(emptyList<MoveButton>(), state.moves)
        assertEquals(TITLE, state.title)
    }

    @Test
    fun BattleUiState_RequestToChoose_EnablesMovesInOrder() {
        val state = BattleUiState.from(session(chooseRequest()))

        assertEquals(BattlePhase.Choose, state.phase)
        assertEquals(
            listOf(MoveButton(1, THUNDERBOLT, 15, 24, enabled = true), MoveButton(2, PROTECT, 0, 16, enabled = false)),
            state.moves,
        )
    }

    @Test
    fun BattleUiState_TerastallizeAllowed_ShowsTeraType() {
        val state = BattleUiState.from(session(chooseRequest(canTerastallize = TERA_TYPE)))

        assertEquals(TERA_TYPE, state.teraType)
    }

    @Test
    fun BattleUiState_TerastallizeNotAllowed_HasNoTeraType() {
        val state = BattleUiState.from(session(chooseRequest(canTerastallize = null)))

        assertEquals(null, state.teraType)
    }

    @Test
    fun BattleUiState_ChoiceSent_IsWaitingWithMovesDisabledAndUndoAvailable() {
        val state = BattleUiState.from(session(chooseRequest()).copy(waitingForOpponent = true))

        assertEquals(BattlePhase.Waiting, state.phase)
        assertTrue(state.moves.none { it.enabled })
        assertTrue(state.party.none { it.canSwitchIn })
        assertTrue(state.canUndo)
    }

    @Test
    fun BattleUiState_ServerSaysWait_IsWaitingWithoutUndo() {
        val state = BattleUiState.from(session(BattleRequest(wait = true, side = side(), rqid = RQID)))

        assertEquals(BattlePhase.Waiting, state.phase)
        assertFalse(state.canUndo)
    }

    @Test
    fun BattleUiState_Choosing_CanSwitchToHealthyBenchOnly() {
        val state = BattleUiState.from(session(chooseRequest()))

        assertEquals(listOf(false, true, false), state.party.map { it.canSwitchIn })
    }

    @Test
    fun BattleUiState_Trapped_CannotSwitch() {
        val state = BattleUiState.from(session(chooseRequest(trapped = true)))

        assertTrue(state.party.none { it.canSwitchIn })
    }

    @Test
    fun BattleUiState_ForcedSwitch_MustSwitchWithMovesDisabled() {
        val request = BattleRequest(forceSwitch = listOf(true), side = side(), rqid = RQID)

        val state = BattleUiState.from(session(request))

        assertEquals(BattlePhase.MustSwitch, state.phase)
        assertTrue(state.moves.none { it.enabled })
        assertEquals(listOf(false, true, false), state.party.map { it.canSwitchIn })
    }

    @Test
    fun BattleUiState_ForcedSwitchWhileTrappedFlagSet_StillAllowsSwitching() {
        val request = BattleRequest(forceSwitch = listOf(true), active = listOf(ActivePokemon(trapped = true)), side = side(), rqid = RQID)

        val state = BattleUiState.from(session(request))

        assertEquals(listOf(false, true, false), state.party.map { it.canSwitchIn })
    }

    @Test
    fun BattleUiState_BattleWon_IsFinishedWithResultAndNoChoices() {
        val state = BattleUiState.from(session(chooseRequest()).copy(result = BattleResult.Won))

        assertEquals(BattlePhase.Finished, state.phase)
        assertEquals(BattleResult.Won, state.result)
        assertTrue(state.moves.none { it.enabled })
        assertFalse(state.canUndo)
    }

    @Test
    fun BattleUiState_PartyMember_HasParsedDetails() {
        val state = BattleUiState.from(session(chooseRequest()))

        assertEquals(
            PartyMember(
                slot = 2,
                species = "Whimsicott",
                level = 84,
                gender = Gender.Female,
                hp = 120,
                maxHp = 238,
                status = Status.Toxic,
                fainted = false,
                active = false,
                item = "leftovers",
                ability = "prankster",
                teraType = "Steel",
                stats = WHIMSICOTT_STATS,
                moves = listOf("moonblast", "encore"),
                canSwitchIn = true,
            ),
            state.party[1],
        )
    }

    @Test
    fun BattleUiState_ErrorAndTurn_ArePassedThrough() {
        val state = BattleUiState.from(session(chooseRequest()).copy(turn = 4, lastError = ERROR))

        assertEquals(4, state.turn)
        assertEquals(ERROR, state.error)
    }

    @Test
    fun BattleUiState_ReplayFirstTurnOfRealBattle_ChooseWithFourMovesAndSixMembers() {
        val sessions = SessionManager(FakeShowdownClient())
        sessions.handle(ShowdownEvent.RoomInit(REPLAY_ROOM, RoomType.BATTLE))
        val upToSecondTurn = transcript().lineSequence().takeWhile { it != SECOND_TURN_LINE }.joinToString(LINE_BREAK)
        ProtocolParser.parse(upToSecondTurn).forEach(sessions::handle)

        val state =
            BattleUiState.from(
                sessions.state.value.sessions
                    .getValue(REPLAY_ROOM),
            )

        assertEquals(BattlePhase.Choose, state.phase)
        assertEquals(4, state.moves.size)
        assertEquals(6, state.party.size)
        assertEquals(1, state.party.count { it.active && !it.canSwitchIn })
    }

    private fun session(request: BattleRequest) = BattleSession(ROOM, title = TITLE, request = request, turn = 1)

    private fun chooseRequest(
        canTerastallize: String? = null,
        trapped: Boolean = false,
    ) = BattleRequest(
        active =
            listOf(
                ActivePokemon(
                    moves =
                        listOf(
                            MoveSlot(THUNDERBOLT, "thunderbolt", pp = 15, maxPp = 24),
                            MoveSlot(PROTECT, "protect", pp = 0, maxPp = 16, disabled = MoveDisabled.Disabled(reason = null)),
                        ),
                    trapped = trapped,
                    canTerastallize = canTerastallize,
                ),
            ),
        side = side(),
        rqid = RQID,
    )

    private fun side() =
        Side(
            name = PLAYER,
            id = SIDE_ID,
            pokemon =
                listOf(
                    SidePokemon("p1: Eelektross", "Eelektross, L87, M", "290/290", active = true),
                    SidePokemon(
                        "p1: Whimsicott",
                        "Whimsicott, L84, F",
                        "120/238 tox",
                        stats = WHIMSICOTT_STATS,
                        moves = listOf("moonblast", "encore"),
                        item = "leftovers",
                        ability = "prankster",
                        teraType = "Steel",
                    ),
                    SidePokemon("p1: Groudon", "Groudon, L72", "0 fnt"),
                ),
        )

    private fun transcript(): String = checkNotNull(javaClass.getResource(TRANSCRIPT_PATH)) { "Missing $TRANSCRIPT_PATH" }.readText()

    private companion object {
        const val ROOM = "battle-gen9randombattle-1"
        const val TITLE = "Alice vs. Bob"
        const val PLAYER = "Alice"
        const val SIDE_ID = "p1"
        const val RQID = 3
        const val THUNDERBOLT = "Thunderbolt"
        const val PROTECT = "Protect"
        const val TERA_TYPE = "Fighting"
        const val ERROR = "[Invalid choice] Can't undo"
        val WHIMSICOTT_STATS = Stats(atk = 117, def = 191, spa = 178, spd = 174, spe = 243)
        const val TRANSCRIPT_PATH = "/battles/gen9-singles.txt"
        const val REPLAY_ROOM = "battle-gen9randombattle-2043118842"
        const val SECOND_TURN_LINE = "|turn|2"
        const val LINE_BREAK = "\n"
    }
}
