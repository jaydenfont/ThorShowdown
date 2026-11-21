package com.example.thorshowdown.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Display 1 (touch): moves, switches, chat. */
@Composable
fun BottomScreen(state: BottomUiState, onAction: (UserAction) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Text(state.message)
    }
}
