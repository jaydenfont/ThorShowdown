package com.example.thorshowdown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.thorshowdown.R
import com.example.thorshowdown.data.BattleFormat
import com.example.thorshowdown.data.SearchError
import com.example.thorshowdown.data.SearchState

private val SCREEN_PADDING = 24.dp
private val ITEM_SPACING = 12.dp

/** Bottom screen once logged in and not in a battle, until the main menu (#8): the search control. */
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
        Text(
            stringResource(R.string.login_logged_in_as, username),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(TestTags.LOBBY_USERNAME),
        )
        SearchControl(state.search, onAction)
    }
}

@Composable
private fun SearchControl(
    search: SearchState,
    onAction: (UserAction) -> Unit,
) {
    when (search) {
        is SearchState.Idle -> {
            search.error?.let { Text(searchErrorText(it), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onAction(UserAction.FindBattle(BattleFormat.Gen9RandomBattle)) },
                modifier = Modifier.testTag(TestTags.LOBBY_FIND_BATTLE),
            ) {
                Text(stringResource(R.string.lobby_find_random_battle))
            }
        }
        is SearchState.Requested, is SearchState.Searching -> {
            CircularProgressIndicator()
            Text(stringResource(R.string.lobby_searching), modifier = Modifier.testTag(TestTags.LOBBY_SEARCHING))
            TextButton(onClick = { onAction(UserAction.CancelSearch) }, modifier = Modifier.testTag(TestTags.LOBBY_CANCEL_SEARCH)) {
                Text(stringResource(R.string.lobby_cancel_search))
            }
        }
    }
}

@Composable
private fun searchErrorText(error: SearchError): String =
    when (error) {
        is SearchError.Refused -> stringResource(R.string.lobby_error_refused, error.message)
        is SearchError.Interrupted -> stringResource(R.string.lobby_error_interrupted, error.message)
        SearchError.NoResponse -> stringResource(R.string.lobby_error_no_response)
        SearchError.SendFailed -> stringResource(R.string.lobby_error_send_failed)
    }
