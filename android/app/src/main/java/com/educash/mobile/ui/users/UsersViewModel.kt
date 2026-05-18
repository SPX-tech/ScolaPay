package com.educash.mobile.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.educash.mobile.data.model.*
import com.educash.mobile.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsersUiState(
    val isLoading: Boolean = true,
    val users: List<User> = emptyList(),
    val error: String? = null,
    val isDeleting: Long? = null
)

data class UserFormUiState(
    val isVisible: Boolean = false,
    val isEditing: Boolean = false,
    val editUserId: Long? = null,
    val isLoading: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val role: String = "CASHIER",
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val repo: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = _uiState

    private val _formState = MutableStateFlow(UserFormUiState())
    val formState: StateFlow<UserFormUiState> = _formState

    init { loadUsers() }

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repo.getUsers()
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isLoading = false, users = it) },
                onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
            )
        }
    }

    fun showCreateForm() {
        _formState.value = UserFormUiState(isVisible = true)
    }

    fun showEditForm(user: User) {
        _formState.value = UserFormUiState(
            isVisible = true,
            isEditing = true,
            editUserId = user.id,
            firstName = user.firstName,
            lastName = user.lastName,
            email = user.email
        )
    }

    fun hideForm() {
        _formState.value = UserFormUiState()
    }

    fun onFirstNameChanged(value: String) { _formState.value = _formState.value.copy(firstName = value, error = null) }
    fun onLastNameChanged(value: String) { _formState.value = _formState.value.copy(lastName = value, error = null) }
    fun onEmailChanged(value: String) { _formState.value = _formState.value.copy(email = value, error = null) }
    fun onPasswordChanged(value: String) { _formState.value = _formState.value.copy(password = value, error = null) }
    fun onRoleChanged(role: String) { _formState.value = _formState.value.copy(role = role) }

    fun saveUser() {
        val state = _formState.value
        if (state.firstName.isBlank() || state.lastName.isBlank() || state.email.isBlank()) {
            _formState.value = state.copy(error = "Tous les champs sont requis")
            return
        }
        if (!state.isEditing && state.password.isBlank()) {
            _formState.value = state.copy(error = "Le mot de passe est requis")
            return
        }

        viewModelScope.launch {
            _formState.value = _formState.value.copy(isLoading = true, error = null)
            val result = if (state.isEditing) {
                repo.updateUser(state.editUserId!!, UpdateUserRequest(
                    firstName = state.firstName,
                    lastName = state.lastName,
                    email = state.email,
                    password = state.password.ifBlank { null },
                    role = state.role
                ))
            } else {
                repo.createUser(CreateUserRequest(
                    email = state.email,
                    password = state.password,
                    firstName = state.firstName,
                    lastName = state.lastName,
                    role = state.role
                ))
            }
            result.fold(
                onSuccess = {
                    _formState.value = _formState.value.copy(isLoading = false, isSaved = true)
                    loadUsers()
                },
                onFailure = { _formState.value = _formState.value.copy(isLoading = false, error = it.message) }
            )
        }
    }

    fun deleteUser(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = id)
            repo.deleteUser(id)
            _uiState.value = _uiState.value.copy(isDeleting = null)
            loadUsers()
        }
    }
}
