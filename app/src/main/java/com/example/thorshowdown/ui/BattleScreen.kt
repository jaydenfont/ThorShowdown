package com.example.thorshowdown.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.thorshowdown.R
import com.example.thorshowdown.data.BattleResult
import com.example.thorshowdown.data.moves.MoveInfo
import com.example.thorshowdown.showdown.Status
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private val SCREEN_PADDING = 12.dp
private val ITEM_SPACING = 8.dp
private val CARD_PADDING = 8.dp
private const val MOVES_PER_ROW = 2
private const val MOVE_LIST_SEPARATOR = ", "
private val TICK = 1.seconds
private const val LOW_TIME_SECONDS = 30
private const val SECONDS_PER_MINUTE = 60
private const val CLOCK_FORMAT = "%d:%02d"
private const val DISABLED_ALPHA = 0.6f

/** Bottom-screen battle menu (#7, functional pass): moves, Tera, party, switching, undo, forfeit. */
@Composable
fun BattleScreen(
    state: BattleUiState,
    onAction: (UserAction) -> Unit,
) {
    var showingParty by rememberSaveable(state.roomId) { mutableStateOf(false) }
    var confirmingForfeit by rememberSaveable(state.roomId) { mutableStateOf(false) }
    var describedSlot by rememberSaveable(state.roomId) { mutableStateOf<Int?>(null) }

    DialogHost {
        Column(
            modifier = Modifier.fillMaxSize().padding(SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_SPACING),
        ) {
            Header(state, onStartTimer = { onAction(UserAction.StartTimer) })
            state.timer?.let { TimerCountdown(it, turn = state.turn) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            when {
                state.phase == BattlePhase.Finished -> Finished(state, onAction)
                state.phase == BattlePhase.MustSwitch -> MustSwitch(state, onAction)
                showingParty ->
                    PartyPanel(
                        party = state.party,
                        // A switch ends the turn's choice, so the next turn opens on the moves, not the party.
                        onAction = { action ->
                            if (action is UserAction.SwitchTo) showingParty = false
                            onAction(action)
                        },
                        onBack = { showingParty = false },
                    )
                else ->
                    ChooseOrWait(
                        state = state,
                        onAction = onAction,
                        onShowParty = { showingParty = true },
                        onForfeit = { confirmingForfeit = true },
                        onDescribe = { slot -> describedSlot = slot },
                    )
            }
        }

        state.moves.firstOrNull { it.slot == describedSlot }?.let { MoveInfoDialog(it, onDismiss = { describedSlot = null }) }
        if (confirmingForfeit) {
            ForfeitDialog(
                onConfirm = {
                    confirmingForfeit = false
                    onAction(UserAction.Forfeit)
                },
                onDismiss = { confirmingForfeit = false },
            )
        }
    }
}

@Composable
private fun Header(
    state: BattleUiState,
    onStartTimer: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.title ?: stringResource(R.string.battle_untitled),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.canStartTimer) {
            TextButton(onClick = onStartTimer) { Text(stringResource(R.string.battle_start_timer)) }
        }
        if (state.turn > 0) Text(stringResource(R.string.battle_turn, state.turn), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ColumnScope.ChooseOrWait(
    state: BattleUiState,
    onAction: (UserAction) -> Unit,
    onShowParty: () -> Unit,
    onForfeit: () -> Unit,
    onDescribe: (slot: Int) -> Unit,
) {
    // Tera applies to the next move only, so the toggle resets every turn.
    var terastallize by rememberSaveable(state.roomId, state.turn) { mutableStateOf(false) }

    if (state.phase == BattlePhase.Waiting) WaitingStatus(state, onAction)
    state.teraType?.let { type ->
        FilterChip(
            selected = terastallize,
            onClick = { terastallize = !terastallize },
            label = { Text(stringResource(R.string.battle_terastallize, type)) },
        )
    }
    MoveGrid(
        moves = state.moves,
        modifier = Modifier.weight(1f),
        onChoose = { slot -> onAction(UserAction.ChooseMove(slot, terastallize)) },
        onDescribe = onDescribe,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING)) {
        OutlinedButton(onClick = onShowParty, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.battle_party)) }
        OutlinedButton(onClick = onForfeit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.battle_forfeit)) }
    }
}

@Composable
private fun WaitingStatus(
    state: BattleUiState,
    onAction: (UserAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        val waitingFor = if (state.moves.isEmpty()) R.string.battle_starting else R.string.battle_waiting
        Text(stringResource(waitingFor), modifier = Modifier.weight(1f))
        if (state.canUndo) {
            TextButton(onClick = { onAction(UserAction.UndoChoice) }) { Text(stringResource(R.string.battle_undo)) }
        }
    }
}

@Composable
private fun MoveGrid(
    moves: List<MoveButton>,
    modifier: Modifier,
    onChoose: (slot: Int) -> Unit,
    onDescribe: (slot: Int) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ITEM_SPACING)) {
        moves.chunked(MOVES_PER_ROW).forEach { row ->
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING)) {
                row.forEach { move ->
                    MoveTile(
                        move = move,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onChoose = { onChoose(move.slot) },
                        onDescribe = { onDescribe(move.slot) },
                    )
                }
            }
        }
    }
}

/** A move button: tap chooses it (when enabled); a long press always shows its description. */
@Composable
private fun MoveTile(
    move: MoveButton,
    modifier: Modifier,
    onChoose: () -> Unit,
    onDescribe: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (move.enabled) colors.primary else colors.surfaceVariant,
        contentColor = if (move.enabled) colors.onPrimary else colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
        modifier = modifier.combinedClickable(onClick = { if (move.enabled) onChoose() }, onLongClick = onDescribe),
    ) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(move.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.battle_move_pp, move.pp, move.maxPp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BoxScope.MoveInfoDialog(
    move: MoveButton,
    onDismiss: () -> Unit,
) {
    InlineDialog(
        onDismissRequest = onDismiss,
        title = { Text(move.info?.name ?: move.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ITEM_SPACING)) {
                move.info?.let { info ->
                    Text(moveSummary(info), fontWeight = FontWeight.Bold)
                    if (info.priority != 0) Text(stringResource(R.string.move_priority, info.priority))
                }
                Text(move.info?.description?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.move_no_description))
                move.disabledReason?.let { Text(stringResource(R.string.move_disabled, it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.move_close)) } },
    )
}

/** e.g. "Electric · Special · Power 90 · Accuracy 100%"; status moves and never-miss moves show a dash. */
@Composable
private fun moveSummary(info: MoveInfo): String {
    val none = stringResource(R.string.move_no_value)
    val power = info.basePower.takeIf { it > 0 }?.toString() ?: none
    val accuracy = info.accuracy?.let { stringResource(R.string.move_accuracy_percent, it) } ?: none
    return stringResource(R.string.move_summary, info.type, info.category, power, accuracy)
}

@Composable
private fun MustSwitch(
    state: BattleUiState,
    onAction: (UserAction) -> Unit,
) {
    Text(stringResource(R.string.battle_must_switch), style = MaterialTheme.typography.titleSmall)
    PartyList(state.party, onAction)
}

@Composable
private fun ColumnScope.PartyPanel(
    party: List<PartyMember>,
    onAction: (UserAction) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.weight(1f)) { PartyList(party, onAction) }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.battle_back)) }
}

/** All six Pokémon; tapping one shows its details and, when allowed, Switch in. */
@Composable
private fun PartyList(
    party: List<PartyMember>,
    onAction: (UserAction) -> Unit,
) {
    var expandedSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(ITEM_SPACING)) {
        items(party, key = PartyMember::slot) { member ->
            PartyMemberCard(
                member = member,
                expanded = expandedSlot == member.slot,
                onToggle = { expandedSlot = member.slot.takeUnless { it == expandedSlot } },
                onSwitchIn = { onAction(UserAction.SwitchTo(member.slot)) },
            )
        }
    }
}

@Composable
private fun PartyMemberCard(
    member: PartyMember,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSwitchIn: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(modifier = Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(ITEM_SPACING / 2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.party_level, member.species, member.level),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
                Text(hpText(member))
                member.status?.let { Text(statusText(it), color = MaterialTheme.colorScheme.error) }
                when {
                    member.fainted -> Text(stringResource(R.string.party_fainted), color = MaterialTheme.colorScheme.error)
                    member.active -> Text(stringResource(R.string.party_active), color = MaterialTheme.colorScheme.primary)
                }
            }
            if (expanded) MemberDetails(member, onSwitchIn)
        }
    }
}

@Composable
private fun MemberDetails(
    member: PartyMember,
    onSwitchIn: () -> Unit,
) {
    val small = MaterialTheme.typography.bodySmall
    if (member.item.isNotEmpty()) Text(stringResource(R.string.party_item, member.item), style = small)
    if (member.ability.isNotEmpty()) Text(stringResource(R.string.party_ability, member.ability), style = small)
    member.teraType?.let { Text(stringResource(R.string.party_tera, it), style = small) }
    member.stats?.let { Text(stringResource(R.string.party_stats, it.atk, it.def, it.spa, it.spd, it.spe), style = small) }
    if (member.moves.isNotEmpty()) Text(stringResource(R.string.party_moves, member.moves.joinToString(MOVE_LIST_SEPARATOR)), style = small)
    if (member.canSwitchIn) {
        Button(onClick = onSwitchIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.battle_switch_in)) }
    }
}

@Composable
private fun Finished(
    state: BattleUiState,
    onAction: (UserAction) -> Unit,
) {
    state.result?.let { Text(resultText(it), style = MaterialTheme.typography.headlineSmall) }
    Button(onClick = { onAction(UserAction.LeaveBattle(state.roomId)) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.battle_leave))
    }
}

@Composable
private fun BoxScope.ForfeitDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    InlineDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battle_forfeit_title)) },
        text = { Text(stringResource(R.string.battle_forfeit_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.battle_forfeit_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.battle_forfeit_dismiss)) } },
    )
}

@Composable
private fun hpText(member: PartyMember): String =
    member.maxHp?.let { stringResource(R.string.party_hp, member.hp, it) }
        ?: stringResource(R.string.party_hp_unknown_max, member.hp)

@Composable
private fun resultText(result: BattleResult): String =
    when (result) {
        BattleResult.Won -> stringResource(R.string.battle_result_won)
        BattleResult.Lost -> stringResource(R.string.battle_result_lost)
        BattleResult.Tied -> stringResource(R.string.battle_result_tied)
    }

@Composable
private fun statusText(status: Status): String =
    when (status) {
        Status.Burn -> stringResource(R.string.status_brn)
        Status.Paralysis -> stringResource(R.string.status_par)
        Status.Sleep -> stringResource(R.string.status_slp)
        Status.Freeze -> stringResource(R.string.status_frz)
        Status.Poison -> stringResource(R.string.status_psn)
        Status.Toxic -> stringResource(R.string.status_tox)
    }

/**
 * The battle timer, counted down once a second from the server's latest reading while it's our turn
 * to choose, frozen while we wait. Restarts whenever a new reading arrives or the turn changes.
 */
@Composable
private fun TimerCountdown(
    timer: BattleTimer,
    turn: Int,
) {
    val reading = timer.timeLeft
    if (reading == null) {
        Text(stringResource(R.string.battle_timer_on), style = MaterialTheme.typography.bodyMedium)
        return
    }
    var elapsedSeconds by remember(reading, turn) { mutableIntStateOf(0) }
    LaunchedEffect(reading, turn, timer.ticking) {
        while (timer.ticking) {
            delay(TICK)
            elapsedSeconds++
        }
    }
    val shown = countdown(reading, elapsedSeconds)
    val color = if (shown.turnSeconds <= LOW_TIME_SECONDS) MaterialTheme.colorScheme.error else Color.Unspecified
    Text(
        stringResource(R.string.battle_timer, clock(shown.turnSeconds), clock(shown.totalSeconds)),
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Seconds as m:ss. */
private fun clock(seconds: Int): String = CLOCK_FORMAT.format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
