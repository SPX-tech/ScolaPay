package com.educash.mobile.ui.settings

import androidx.lifecycle.ViewModel
import com.educash.mobile.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SettingsUiState(
    val userName: String = "",
    val userRole: String = "",
    val apiUrl: String = ""
)

class SettingsViewModel : ViewModel() {

    private val authRepo = AuthRepository()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        _uiState.value = SettingsUiState(
            userName = authRepo.getCurrentUserName() ?: "",
            userRole = authRepo.getCurrentUserRole() ?: ""
        )
    }

    fun logout() {
        authRepo.logout()
    }
}
