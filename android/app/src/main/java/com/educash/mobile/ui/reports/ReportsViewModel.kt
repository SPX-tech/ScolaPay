package com.educash.mobile.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.educash.mobile.data.model.RevenueReport
import com.educash.mobile.data.model.StudentDebt
import com.educash.mobile.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ReportsUiState(
    val isLoading: Boolean = false,
    val selectedTab: Int = 0,
    val startDate: String = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
    val endDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val revenueReport: RevenueReport? = null,
    val debtsReport: List<StudentDebt> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repo: ReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState

    fun onTabChanged(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        if (tab == 0) loadRevenueReport()
        else loadDebtsReport()
    }

    fun onStartDateChanged(date: String) {
        _uiState.value = _uiState.value.copy(startDate = date)
    }

    fun onEndDateChanged(date: String) {
        _uiState.value = _uiState.value.copy(endDate = date)
    }

    fun loadRevenueReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repo.getRevenueReport(_uiState.value.startDate, _uiState.value.endDate)
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isLoading = false, revenueReport = it) },
                onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
            )
        }
    }

    fun loadDebtsReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repo.getDebtsReport()
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isLoading = false, debtsReport = it) },
                onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
            )
        }
    }
}
