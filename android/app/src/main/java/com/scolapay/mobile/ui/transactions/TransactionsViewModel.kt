package com.scolapay.mobile.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scolapay.mobile.data.model.Transaction
import com.scolapay.mobile.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val selectedStatus: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val error: String? = null,
    val voidingTransactionId: Long? = null
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repo: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState

    init { loadTransactions() }

    fun loadTransactions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repo.getTransactions(
                page = _uiState.value.currentPage,
                status = _uiState.value.selectedStatus,
                startDate = _uiState.value.startDate,
                endDate = _uiState.value.endDate
            )
            result.fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        transactions = response.data,
                        totalPages = response.meta.totalPages
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
            )
        }
    }

    fun onStatusFilterChanged(status: String?) {
        _uiState.value = _uiState.value.copy(selectedStatus = status, currentPage = 0)
        loadTransactions()
    }

    fun voidTransaction(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(voidingTransactionId = id)
            repo.voidTransaction(id)
            _uiState.value = _uiState.value.copy(voidingTransactionId = null)
            loadTransactions()
        }
    }
}
