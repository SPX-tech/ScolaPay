package com.scolapay.mobile.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scolapay.mobile.data.model.DashboardSummary
import com.scolapay.mobile.data.model.MonthlyReport
import com.scolapay.mobile.data.repository.AuthRepository
import com.scolapay.mobile.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val summary: DashboardSummary? = null,
    val monthlyReport: MonthlyReport? = null,
    val userName: String = "",
    val userRole: String = "",
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepo: DashboardRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                userName = authRepo.getCurrentUserName() ?: "",
                userRole = authRepo.getCurrentUserRole() ?: ""
            )

            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1

            val summaryResult = dashboardRepo.getSummary()
            val reportResult = dashboardRepo.getMonthlyReport(year, month)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                summary = summaryResult.getOrNull(),
                monthlyReport = reportResult.getOrNull(),
                error = if (summaryResult.isFailure) summaryResult.exceptionOrNull()?.message else null
            )
        }
    }
}
