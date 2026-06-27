package com.scolapay.mobile.ui.settings

import androidx.lifecycle.ViewModel
import com.scolapay.mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "",
    val userRole: String = "",
    val apiUrl: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {

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
