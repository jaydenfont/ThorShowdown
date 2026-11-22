package com.example.thorshowdown.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Display 1 (touch): login, then the lobby; moves, switches and chat once battles land (#7). */
@Composable
fun BottomScreen(
    state: BottomUiState,
    onAction: (UserAction) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        val login = state.login
        val lobby = state.lobby
        if (login is LoginUiState.LoggedIn && lobby != null) {
            LobbyScreen(login.username, lobby, onAction)
        } else {
            LoginScreen(login, onAction)
        }
    }
}
