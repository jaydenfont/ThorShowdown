package com.example.thorshowdown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.thorshowdown.R
import com.example.thorshowdown.data.LoginError
import com.example.thorshowdown.showdown.LoginFailure

private val SCREEN_PADDING = 24.dp
private val ITEM_SPACING = 12.dp

/** Bottom-screen login: saved-login choice, form, or progress. */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onAction: (UserAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(ITEM_SPACING, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleLarge)
        when (state) {
            LoginUiState.Loading -> Text(stringResource(R.string.login_loading))
            is LoginUiState.ChooseSaved -> SavedLoginChoice(state, onAction)
            is LoginUiState.Form -> LoginForm(state, onAction)
            is LoginUiState.LoggingIn -> LoggingIn(state.username)
            is LoginUiState.LoggedIn -> Text(stringResource(R.string.login_logged_in_as, state.username))
        }
    }
}

@Composable
private fun SavedLoginChoice(
    state: LoginUiState.ChooseSaved,
    onAction: (UserAction) -> Unit,
) {
    state.error?.let { LoginErrorText(it) }
    Button(onClick = { onAction(UserAction.ContinueAsSaved) }) {
        Text(stringResource(R.string.login_continue_as, state.username))
    }
    TextButton(onClick = { onAction(UserAction.UseDifferentAccount) }) {
        Text(stringResource(R.string.login_use_different_account))
    }
}

@Composable
private fun LoginForm(
    state: LoginUiState.Form,
    onAction: (UserAction) -> Unit,
) {
    var username by rememberSaveable(state.username) { mutableStateOf(state.username) }
    // Deliberately not saveable: the password shouldn't outlive the composition.
    var password by remember { mutableStateOf("") }
    val canSubmit = username.isNotBlank() && password.isNotEmpty()

    state.error?.let { LoginErrorText(it) }
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text(stringResource(R.string.login_username)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    )
    Button(onClick = { onAction(UserAction.LogIn(username.trim(), password)) }, enabled = canSubmit) {
        Text(stringResource(R.string.login_button))
    }
}

@Composable
private fun LoggingIn(username: String) {
    CircularProgressIndicator()
    Text(stringResource(R.string.login_logging_in_as, username))
}

@Composable
private fun LoginErrorText(error: LoginError) {
    val text =
        when (error.reason) {
            LoginFailure.NotConnected -> stringResource(R.string.login_error_not_connected)
            LoginFailure.WrongPassword -> stringResource(R.string.login_error_wrong_password, error.username)
            LoginFailure.NameTaken -> stringResource(R.string.login_error_name_taken, error.username, error.message)
            LoginFailure.Network -> stringResource(R.string.login_error_network)
            LoginFailure.Timeout -> stringResource(R.string.login_error_timeout)
        }
    Text(text, color = MaterialTheme.colorScheme.error)
}
