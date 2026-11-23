package com.example.thorshowdown.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.thorshowdown.R
import com.example.thorshowdown.ui.web.BattleWebView

/**
 * Display 0: the current battle in Showdown's web client (view only), or a status line.
 * The WebView is created once logged in and kept while idle, hidden behind the status.
 */
@Composable
fun TopScreen(state: TopUiState) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.loggedInUsername != null) {
            BattleWebView(state.battleRoomId, Modifier.fillMaxSize())
        }
        if (state.battleRoomId == null) {
            StatusText(state.loggedInUsername)
        }
    }
}

@Composable
private fun StatusText(loggedInUsername: String?) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            val text =
                loggedInUsername
                    ?.let { stringResource(R.string.top_no_battle, it) }
                    ?: stringResource(R.string.top_log_in_below)
            Text(text, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
