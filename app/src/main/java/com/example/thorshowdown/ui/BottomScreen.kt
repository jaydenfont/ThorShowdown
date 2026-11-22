package com.example.thorshowdown.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Display 1 (touch): login now; moves, switches and chat once battles land (#7). */
@Composable
fun BottomScreen(
    state: BottomUiState,
    onAction: (UserAction) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        LoginScreen(state.login, onAction)
    }
}
