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

/** Display 0: battlefield. Until the WebView arrives (#4), shows the login status. */
@Composable
fun TopScreen(state: TopUiState) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            val text =
                state.loggedInUsername
                    ?.let { stringResource(R.string.top_logged_in_as, it) }
                    ?: stringResource(R.string.top_log_in_below)
            Text(text, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
