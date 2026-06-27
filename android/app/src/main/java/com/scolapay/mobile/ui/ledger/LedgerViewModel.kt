package com.scolapay.mobile.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scolapay.mobile.data.model.SchoolClass
import com.scolapay.mobile.data.model.Student
import com.scolapay.mobile.data.repository.ClassRepository
import com.scolapay.mobile.data.repository.LedgerRepository
import com.scolapay.mobile.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LedgerUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val students: List<Student> = emptyList(),
    val classes: List<SchoolClass> = emptyList(),
    val selectedClassId: Long? = null,
    val selectedStudentId: Long? = null,
    val amount: String = "",
    val description: String = "",
    val error: String? = null,
    val success: Boolean = false,
    val searchQuery: String = ""
)

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val ledgerRepo: LedgerRepository,
    private val studentRepo: StudentRepository,
    private val classRepo: ClassRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState

    init { loadInitialData() }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val studentsResult = studentRepo.getStudents(limit = 200)
            val classesResult = classRepo.getClasses()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                students = studentsResult.getOrNull()?.data ?: emptyList(),
                classes = classesResult.getOrNull() ?: emptyList()
            )
        }
    }

    fun onClassFilterChanged(classId: Long?) {
        _uiState.value = _uiState.value.copy(selectedClassId = classId, selectedStudentId = null)
    }

    fun onStudentSelected(studentId: Long) {
        _uiState.value = _uiState.value.copy(selectedStudentId = studentId)
    }

    fun onAmountChanged(amount: String) {
        _uiState.value = _uiState.value.copy(amount = amount, error = null)
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    val filteredStudents: List<Student>
        get() {
            val state = _uiState.value
            var result = state.students
            if (state.selectedClassId != null) {
                result = result.filter { it.classId == state.selectedClassId }
            }
            if (state.searchQuery.isNotBlank()) {
                val query = state.searchQuery.lowercase()
                result = result.filter {
                    it.firstName.lowercase().contains(query) ||
                    it.lastName.lowercase().contains(query) ||
                    (it.matricule?.lowercase()?.contains(query) == true)
                }
            }
            return result
        }

    fun recordPayment() {
        val state = _uiState.value
        if (state.selectedStudentId == null) {
            _uiState.value = state.copy(error = "Veuillez sélectionner un élève")
            return
        }
        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.value = state.copy(error = "Montant invalide")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val result = ledgerRepo.recordCashPayment(
                studentId = state.selectedStudentId!!,
                amount = amount,
                description = state.description.ifBlank { null }
            )
            result.fold(
                onSuccess = {
                    _uiState.value = LedgerUiState(
                        isLoading = false,
                        students = state.students,
                        classes = state.classes,
                        success = true
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = it.message
                    )
                }
            )
        }
    }

    fun resetSuccess() {
        _uiState.value = _uiState.value.copy(success = false)
    }
}
