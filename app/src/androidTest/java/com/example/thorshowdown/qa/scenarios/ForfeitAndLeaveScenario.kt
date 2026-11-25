package com.example.thorshowdown.qa.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.thorshowdown.qa.Canaries
import com.example.thorshowdown.qa.Fixture
import com.example.thorshowdown.qa.QaRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Forfeit a battle after confirming, see the loss, then leave back to the lobby. Spec: qa/scenarios/ForfeitAndLeave.md. */
@RunWith(AndroidJUnit4::class)
class ForfeitAndLeaveScenario {
    @get:Rule val qa = QaRule()

    @Test
    fun ForfeitAndLeave_ConfirmForfeitThenLeave_ReturnsToLobby() =
        runBlocking {
            qa.logInToLobby()
            Fixture.BattleStart.pushTo(qa.server)
            qa.battle.assertMovesShown(count = MOVE_COUNT)

            qa.battle.forfeit()
            qa.checkpoint(ForfeitAndLeaveCheckpoint.ConfirmForfeit)
            qa.battle.confirmForfeit()
            qa.server.awaitSent(FORFEIT)

            Fixture.Forfeited.pushTo(qa.server)
            qa.battle.assertFinished()
            qa.checkpoint(ForfeitAndLeaveCheckpoint.Lost)

            qa.battle.leave()
            qa.server.awaitSent(LEAVE)
            Fixture.Deinit.pushTo(qa.server)
            qa.lobby.assertShownFor(Canaries.USERNAME)
            qa.checkpoint(ForfeitAndLeaveCheckpoint.BackInLobby)
        }

    private companion object {
        const val MOVE_COUNT = 4
        const val FORFEIT = "/forfeit"
        const val LEAVE = "/leave ${Fixture.ROOM_ID}"
    }
}

/** Screenshot points; each has a heading in qa/scenarios/ForfeitAndLeave.md. */
enum class ForfeitAndLeaveCheckpoint { ConfirmForfeit, Lost, BackInLobby }
