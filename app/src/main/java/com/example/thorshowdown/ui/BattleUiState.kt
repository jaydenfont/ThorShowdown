package com.example.thorshowdown.ui

import com.example.thorshowdown.data.BattleResult
import com.example.thorshowdown.data.BattleSession
import com.example.thorshowdown.showdown.ActivePokemon
import com.example.thorshowdown.showdown.Gender
import com.example.thorshowdown.showdown.MoveDisabled
import com.example.thorshowdown.showdown.PokemonCondition
import com.example.thorshowdown.showdown.PokemonDetails
import com.example.thorshowdown.showdown.SidePokemon
import com.example.thorshowdown.showdown.Stats
import com.example.thorshowdown.showdown.Status
import com.example.thorshowdown.showdown.TimeLeft

/** What the battle menu lets the player do right now. */
enum class BattlePhase { Choose, MustSwitch, Waiting, Finished }

/** The bottom-screen battle menu for the current battle. */
data class BattleUiState(
    val roomId: String,
    val title: String?,
    val turn: Int,
    val phase: BattlePhase,
    val moves: List<MoveButton>,
    /** Non-null when Terastallizing is allowed this turn. */
    val teraType: String?,
    val party: List<PartyMember>,
    val canUndo: Boolean,
    /** Whether the Timer button is shown: the timer is off and the battle isn't over. */
    val canStartTimer: Boolean,
    /** The battle timer while it runs and the battle isn't over; null otherwise. */
    val timer: BattleTimer?,
    val result: BattleResult?,
    val error: String?,
) {
    companion object {
        /** Builds the menu from the session's latest `|request|`; the server's request is the source of truth. */
        fun from(session: BattleSession): BattleUiState {
            val phase = phaseOf(session)
            val active = session.request?.active?.firstOrNull()
            return BattleUiState(
                roomId = session.roomId,
                title = session.title,
                turn = session.turn,
                phase = phase,
                moves = movesOf(active, phase),
                teraType = active?.canTerastallize.takeIf { phase == BattlePhase.Choose },
                party = partyOf(session, phase, trapped = active?.trapped == true),
                canUndo = session.canUndo,
                canStartTimer = !session.timerOn && phase != BattlePhase.Finished,
                timer =
                    BattleTimer(session.timeLeft, ticking = session.awaitingChoice).takeIf {
                        session.timerOn &&
                            phase != BattlePhase.Finished
                    },
                result = session.result,
                error = session.lastError,
            )
        }

        private fun phaseOf(session: BattleSession): BattlePhase {
            val request = session.request
            return when {
                session.result != null -> BattlePhase.Finished
                request == null || !session.awaitingChoice -> BattlePhase.Waiting
                request.forceSwitch.any { it } -> BattlePhase.MustSwitch
                else -> BattlePhase.Choose
            }
        }

        private fun movesOf(
            active: ActivePokemon?,
            phase: BattlePhase,
        ): List<MoveButton> =
            active?.moves.orEmpty().mapIndexed { index, move ->
                MoveButton(
                    slot = index + 1,
                    name = move.move,
                    pp = move.pp,
                    maxPp = move.maxPp,
                    enabled = phase == BattlePhase.Choose && move.disabled == MoveDisabled.Enabled,
                )
            }

        private fun partyOf(
            session: BattleSession,
            phase: BattlePhase,
            trapped: Boolean,
        ): List<PartyMember> {
            val switchingAllowed = phase == BattlePhase.MustSwitch || (phase == BattlePhase.Choose && !trapped)
            return session.request?.side?.pokemon.orEmpty().mapIndexed { index, pokemon ->
                memberOf(index + 1, pokemon, switchingAllowed)
            }
        }

        private fun memberOf(
            slot: Int,
            pokemon: SidePokemon,
            switchingAllowed: Boolean,
        ): PartyMember {
            val details = PokemonDetails.parse(pokemon.details)
            val condition = PokemonCondition.parse(pokemon.condition)
            return PartyMember(
                slot = slot,
                species = details.species,
                level = details.level,
                gender = details.gender,
                hp = condition.hp,
                maxHp = condition.maxHp,
                status = condition.status,
                fainted = condition.fainted,
                active = pokemon.active,
                item = pokemon.item,
                ability = pokemon.ability,
                teraType = pokemon.teraType,
                stats = pokemon.stats,
                moves = pokemon.moves,
                canSwitchIn = switchingAllowed && !condition.fainted && !pokemon.active,
            )
        }
    }
}

/**
 * The running battle timer. [timeLeft] is the server's latest reading (null until one arrives);
 * it counts down only while [ticking], i.e. while it's our turn to choose.
 */
data class BattleTimer(
    val timeLeft: TimeLeft?,
    val ticking: Boolean,
)

/** [timeLeft] after [elapsedSeconds] of our clock running, never below zero. */
fun countdown(
    timeLeft: TimeLeft,
    elapsedSeconds: Int,
): TimeLeft =
    TimeLeft(
        turnSeconds = (timeLeft.turnSeconds - elapsedSeconds).coerceAtLeast(0),
        totalSeconds = (timeLeft.totalSeconds - elapsedSeconds).coerceAtLeast(0),
    )

/** One move button; [slot] is 1-based, as sent in the choice. */
data class MoveButton(
    val slot: Int,
    val name: String,
    val pp: Int,
    val maxPp: Int,
    val enabled: Boolean,
)

/** One of the player's Pokémon; [slot] is 1-based in the request's team order, as sent in a switch. */
data class PartyMember(
    val slot: Int,
    val species: String,
    val level: Int,
    val gender: Gender?,
    val hp: Int,
    val maxHp: Int?,
    val status: Status?,
    val fainted: Boolean,
    val active: Boolean,
    val item: String,
    val ability: String,
    val teraType: String?,
    val stats: Stats?,
    val moves: List<String>,
    val canSwitchIn: Boolean,
)
