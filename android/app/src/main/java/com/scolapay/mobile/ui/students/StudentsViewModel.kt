package com.scolapay.mobile.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scolapay.mobile.data.model.*
import com.scolapay.mobile.data.repository.ClassRepository
import com.scolapay.mobile.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentsListUiState(
    val isLoading: Boolean = true,
    val students: List<Student> = emptyList(),
    val classes: List<SchoolClass> = emptyList(),
    val searchQuery: String = "",
    val selectedClassId: Long? = null,
    val error: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 1
)

data class StudentDetailUiState(
    val isLoading: Boolean = true,
    val student: Student? = null,
    val error: String? = null
)

data class StudentFormUiState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val classes: List<SchoolClass> = emptyList(),
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val classId: Long? = null,
    val matricule: String = "",
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class StudentsViewModel @Inject constructor(
    private val studentRepo: StudentRepository,
    private val classRepo: ClassRepository
) : ViewModel() {

    private val _listState = MutableStateFlow(StudentsListUiState())
    val listState: StateFlow<StudentsListUiState> = _listState

    private val _detailState = MutableStateFlow(StudentDetailUiState())
    val detailState: StateFlow<StudentDetailUiState> = _detailState

    private val _formState = MutableStateFlow(StudentFormUiState())
    val formState: StateFlow<StudentFormUiState> = _formState

    init { loadStudents() }

    fun loadStudents() {
        viewModelScope.launch {
            _listState.value = _listState.value.copy(isLoading = true, error = null)
            val studentsResult = studentRepo.getStudents(
                page = _listState.value.currentPage,
                search = _listState.value.searchQuery.ifBlank { null },
                classId = _listState.value.selectedClassId
            )
            val classesResult = classRepo.getClasses()

            _listState.value = _listState.value.copy(
                isLoading = false,
                students = studentsResult.getOrNull()?.data ?: emptyList(),
                totalPages = studentsResult.getOrNull()?.meta?.totalPages ?: 1,
                classes = classesResult.getOrNull() ?: _listState.value.classes,
                error = studentsResult.exceptionOrNull()?.message
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _listState.value = _listState.value.copy(searchQuery = query)
        loadStudents()
    }

    fun onClassFilterChanged(classId: Long?) {
        _listState.value = _listState.value.copy(selectedClassId = classId, currentPage = 0)
        loadStudents()
    }

    fun loadStudent(id: Long) {
        viewModelScope.launch {
            _detailState.value = StudentDetailUiState(isLoading = true)
            val result = studentRepo.getStudent(id)
            _detailState.value = StudentDetailUiState(
                isLoading = false,
                student = result.getOrNull(),
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun loadForm(studentId: Long? = null) {
        viewModelScope.launch {
            val classesResult = classRepo.getClasses()
            _formState.value = _formState.value.copy(classes = classesResult.getOrNull() ?: emptyList())

            if (studentId != null) {
                val result = studentRepo.getStudent(studentId)
                result.getOrNull()?.let { student ->
                    _formState.value = _formState.value.copy(
                        isEditing = true,
                        firstName = student.firstName,
                        lastName = student.lastName,
                        email = student.email ?: "",
                        phone = student.phone ?: "",
                        classId = student.classId,
                        matricule = student.matricule ?: ""
                    )
                }
            }
        }
    }

    fun onFirstNameChanged(value: String) { _formState.value = _formState.value.copy(firstName = value, error = null) }
    fun onLastNameChanged(value: String) { _formState.value = _formState.value.copy(lastName = value, error = null) }
    fun onEmailChanged(value: String) { _formState.value = _formState.value.copy(email = value) }
    fun onPhoneChanged(value: String) { _formState.value = _formState.value.copy(phone = value) }
    fun onClassChanged(classId: Long) { _formState.value = _formState.value.copy(classId = classId) }
    fun onMatriculeChanged(value: String) { _formState.value = _formState.value.copy(matricule = value) }

    fun saveStudent() {
        val state = _formState.value
        if (state.firstName.isBlank() || state.lastName.isBlank() || state.classId == null) {
            _formState.value = state.copy(error = "Prénom, nom et classe sont requis")
            return
        }

        viewModelScope.launch {
            _formState.value = _formState.value.copy(isLoading = true, error = null)
            val result = if (state.isEditing) {
                Result.success(null)
            } else {
                studentRepo.createStudent(
                    CreateStudentRequest(
                        firstName = state.firstName,
                        lastName = state.lastName,
                        email = state.email.ifBlank { null },
                        phone = state.phone.ifBlank { null },
                        classId = state.classId!!,
                        matricule = state.matricule.ifBlank { null }
                    )
                )
            }
            result.fold(
                onSuccess = { _formState.value = _formState.value.copy(isLoading = false, isSaved = true) },
                onFailure = { _formState.value = _formState.value.copy(isLoading = false, error = it.message) }
            )
        }
    }

    fun updateStudent(studentId: Long) {
        val state = _formState.value
        viewModelScope.launch {
            _formState.value = _formState.value.copy(isLoading = true, error = null)
            val result = studentRepo.updateStudent(
                studentId,
                UpdateStudentRequest(
                    firstName = state.firstName.ifBlank { null },
                    lastName = state.lastName.ifBlank { null },
                    email = state.email.ifBlank { null },
                    phone = state.phone.ifBlank { null },
                    classId = state.classId
                )
            )
            result.fold(
                onSuccess = { _formState.value = _formState.value.copy(isLoading = false, isSaved = true) },
                onFailure = { _formState.value = _formState.value.copy(isLoading = false, error = it.message) }
            )
        }
    }

    fun deleteStudent(id: Long) {
        viewModelScope.launch {
            studentRepo.deleteStudent(id)
            loadStudents()
        }
    }
}
