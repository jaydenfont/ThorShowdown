package com.example.thorshowdown.qa.robots

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.thorshowdown.ui.TestTags

/** The bottom-screen battle menu. The only place workflow tests learn how it is laid out. */
class BattleRobot(
    private val compose: ComposeTestRule,
) {
    /** Waits until moves 1..[count] are shown and can be chosen. */
    fun assertMovesShown(count: Int) {
        (1..count).forEach { slot ->
            compose.awaitTag(TestTags.move(slot))
            compose.onNodeWithTag(TestTags.move(slot)).assertIsEnabled()
        }
    }

    fun chooseMove(slot: Int) {
        compose.onNodeWithTag(TestTags.move(slot)).performClick()
    }

    /** Waits for the "waiting for your opponent" state with its Cancel choice button. */
    fun assertWaitingWithUndo() {
        compose.awaitTag(TestTags.BATTLE_WAITING)
        compose.awaitTag(TestTags.BATTLE_UNDO)
    }

    fun undo() {
        compose.onNodeWithTag(TestTags.BATTLE_UNDO).performClick()
    }

    fun openParty() {
        compose.onNodeWithTag(TestTags.BATTLE_PARTY).performClick()
        compose.awaitTag(TestTags.partyMember(1))
    }

    /** Expands party slot [slot] and waits for its Switch in button. */
    fun expandMember(slot: Int) {
        compose.onNodeWithTag(TestTags.partyMember(slot)).performClick()
        compose.awaitTag(TestTags.switchIn(slot))
    }

    fun switchIn(slot: Int) {
        compose.onNodeWithTag(TestTags.switchIn(slot)).performClick()
    }

    /** Waits for the forced-switch prompt after our Pokémon fainted. */
    fun assertMustSwitch() {
        compose.awaitTag(TestTags.BATTLE_MUST_SWITCH)
    }

    /** Opens the forfeit confirmation and confirms it. */
    fun forfeit() {
        compose.onNodeWithTag(TestTags.BATTLE_FORFEIT).performClick()
        compose.awaitTag(TestTags.BATTLE_FORFEIT_CONFIRM)
    }

    fun confirmForfeit() {
        compose.onNodeWithTag(TestTags.BATTLE_FORFEIT_CONFIRM).performClick()
    }

    /** Waits for the finished state: the result and Leave battle. */
    fun assertFinished() {
        compose.awaitTag(TestTags.BATTLE_RESULT)
        compose.awaitTag(TestTags.BATTLE_LEAVE)
    }

    fun leave() {
        compose.onNodeWithTag(TestTags.BATTLE_LEAVE).performClick()
    }
}
