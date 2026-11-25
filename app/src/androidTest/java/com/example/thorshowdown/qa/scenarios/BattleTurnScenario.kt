package com.example.thorshowdown.qa.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.thorshowdown.qa.Fixture
import com.example.thorshowdown.qa.QaRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One turn of a battle: choose a move and undo it, switch to Lucario, which faints, then the
 * forced switch to Landorus-Therian. Spec: qa/scenarios/BattleTurn.md.
 */
@RunWith(AndroidJUnit4::class)
class BattleTurnScenario {
    @get:Rule val qa = QaRule()

    @Test
    fun BattleTurn_ChooseUndoSwitchAndForcedSwitch_SendsEachChoice() =
        runBlocking {
            qa.logInToLobby()
            Fixture.BattleStart.pushTo(qa.server)
            qa.battle.assertMovesShown(count = MOVE_COUNT)
            qa.checkpoint(BattleTurnCheckpoint.MovesShown)

            qa.battle.chooseMove(1)
            qa.server.awaitSent(MOVE_1)
            qa.battle.assertWaitingWithUndo()
            qa.checkpoint(BattleTurnCheckpoint.ChoiceSent)

            qa.battle.undo()
            qa.server.awaitSent(UNDO)
            qa.battle.assertMovesShown(count = MOVE_COUNT)

            qa.battle.openParty()
            qa.battle.expandMember(LUCARIO)
            qa.checkpoint(BattleTurnCheckpoint.PartyOpen)
            qa.battle.switchIn(LUCARIO)
            qa.server.awaitSent(SWITCH_TO_LUCARIO)

            Fixture.LucarioFaints.pushTo(qa.server)
            qa.battle.assertMustSwitch()
            qa.checkpoint(BattleTurnCheckpoint.ForcedSwitch)

            qa.battle.expandMember(LANDORUS_AFTER_FAINT)
            qa.battle.switchIn(LANDORUS_AFTER_FAINT)
            qa.server.awaitSent(SWITCH_TO_LANDORUS)
            Fixture.TurnTwo.pushTo(qa.server)
            qa.battle.assertMovesShown(count = MOVE_COUNT)
            qa.checkpoint(BattleTurnCheckpoint.TurnTwo)
        }

    private companion object {
        const val MOVE_COUNT = 4
        const val LUCARIO = 2

        /** The server lists the active Pokémon first, so after Lucario came in Landorus is third. */
        const val LANDORUS_AFTER_FAINT = 3
        const val MOVE_1 = "/choose move 1"
        const val UNDO = "/choose undo"
        const val SWITCH_TO_LUCARIO = "/choose switch 2"
        const val SWITCH_TO_LANDORUS = "/choose switch 3"
    }
}

/** Screenshot points; each has a heading in qa/scenarios/BattleTurn.md. */
enum class BattleTurnCheckpoint { MovesShown, ChoiceSent, PartyOpen, ForcedSwitch, TurnTwo }
