package com.example.thorshowdown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.thorshowdown.R
import com.example.thorshowdown.data.BattleFormat
import com.example.thorshowdown.data.BattleResult
import com.example.thorshowdown.data.SearchError
import com.example.thorshowdown.data.SearchState

private val SCREEN_PADDING = 24.dp
private val ITEM_SPACING = 12.dp
private val CARD_PADDING = 16.dp

/** Bottom screen once logged in, until the main menu (#8): the current battle and the search control. */
@Composable
fun LobbyScreen(
    username: String,
    state: LobbyUiState,
    onAction: (UserAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(ITEM_SPACING, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.login_logged_in_as, username), style = MaterialTheme.typography.titleMedium)
        state.battle?.let { BattleCardView(it, onAction) }
        SearchControl(state.search, onAction)
    }
}

@Composable
private fun BattleCardView(
    battle: BattleCard,
    onAction: (UserAction) -> Unit,
) {
    var confirmingForfeit by rememberSaveable(battle.roomId) { mutableStateOf(false) }
    val leave = { onAction(UserAction.LeaveBattle(battle.roomId)) }

    Card {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_SPACING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(battle.title ?: stringResource(R.string.lobby_battle_untitled), style = MaterialTheme.typography.titleLarge)
            battle.result?.let { Text(resultText(it)) }
            Button(onClick = { if (battle.result == null) confirmingForfeit = true else leave() }) {
                Text(stringResource(R.string.lobby_leave_battle))
            }
        }
    }
    if (confirmingForfeit) {
        ForfeitDialog(
            onConfirm = {
                confirmingForfeit = false
                leave()
            },
            onDismiss = { confirmingForfeit = false },
        )
    }
}

@Composable
private fun ForfeitDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lobby_forfeit_title)) },
        text = { Text(stringResource(R.string.lobby_forfeit_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.lobby_forfeit_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lobby_forfeit_dismiss)) } },
    )
}

@Composable
private fun SearchControl(
    search: SearchState,
    onAction: (UserAction) -> Unit,
) {
    when (search) {
        is SearchState.Idle -> {
            search.error?.let { Text(searchErrorText(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onAction(UserAction.FindBattle(BattleFormat.Gen9RandomBattle)) }) {
                Text(stringResource(R.string.lobby_find_random_battle))
            }
        }
        is SearchState.Requested, is SearchState.Searching -> {
            CircularProgressIndicator()
            Text(stringResource(R.string.lobby_searching))
            TextButton(onClick = { onAction(UserAction.CancelSearch) }) {
                Text(stringResource(R.string.lobby_cancel_search))
            }
        }
    }
}

@Composable
private fun resultText(result: BattleResult): String =
    when (result) {
        BattleResult.Won -> stringResource(R.string.lobby_result_won)
        BattleResult.Lost -> stringResource(R.string.lobby_result_lost)
        BattleResult.Tied -> stringResource(R.string.lobby_result_tied)
    }

@Composable
private fun searchErrorText(error: SearchError): String =
    when (error) {
        is SearchError.Refused -> stringResource(R.string.lobby_error_refused, error.message)
        is SearchError.Interrupted -> stringResource(R.string.lobby_error_interrupted, error.message)
        SearchError.NoResponse -> stringResource(R.string.lobby_error_no_response)
        SearchError.SendFailed -> stringResource(R.string.lobby_error_send_failed)
    }
