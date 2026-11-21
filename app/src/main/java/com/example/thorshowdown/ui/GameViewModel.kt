package com.example.thorshowdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.thorshowdown.ThorShowdownApp
import com.example.thorshowdown.data.ShowdownRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TopUiState(val message: String = "Hello World (top)")
data class BottomUiState(val message: String = "Hello World (bottom)")

sealed interface UserAction {
    data class SendCommand(val command: String) : UserAction
}

/** Shared by both displays. Turns repository state into per-screen UI state. */
class GameViewModel(private val repository: ShowdownRepository) : ViewModel() {

    private val _topState = MutableStateFlow(TopUiState())
    val topState: StateFlow<TopUiState> = _topState.asStateFlow()

    private val _bottomState = MutableStateFlow(BottomUiState())
    val bottomState: StateFlow<BottomUiState> = _bottomState.asStateFlow()

    fun onAction(action: UserAction) {
        when (action) {
            is UserAction.SendCommand -> viewModelScope.launch { repository.sendCommand(action.command) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThorShowdownApp
                GameViewModel(app.repository)
            }
        }
    }
}
