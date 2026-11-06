package com.scolapay.mobile.ui.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.scolapay.mobile.data.model.User
import com.scolapay.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    viewModel: UsersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    // User form dialog
    if (formState.isVisible) {
        UserFormDialog(
            formState = formState,
            onFirstNameChanged = viewModel::onFirstNameChanged,
            onLastNameChanged = viewModel::onLastNameChanged,
            onEmailChanged = viewModel::onEmailChanged,
            onPasswordChanged = viewModel::onPasswordChanged,
            onRoleChanged = viewModel::onRoleChanged,
            onSave = viewModel::saveUser,
            onDismiss = viewModel::hideForm
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Utilisateurs", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showCreateForm, containerColor = Blue700) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.users.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null,
                        modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Aucun utilisateur", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.users) { user ->
                    UserCard(
                        user = user,
                        onEdit = { viewModel.showEditForm(user) },
                        onDelete = { viewModel.deleteUser(user.id) },
                        isDeleting = uiState.isDeleting == user.id
                    )
                }
            }
        }
    }
}

@Composable
fun UserCard(
    user: User,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isDeleting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${user.firstName} ${user.lastName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (user.role) {
                        "ADMIN" -> Orange50
                        "CASHIER" -> Blue50
                        else -> Grey50
                    }
                ) {
                    Text(
                        text = when (user.role) {
                            "ADMIN" -> "Administrateur"
                            "CASHIER" -> "Caissier"
                            else -> user.role
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (!user.isActive) {
                Text("Inactif", style = MaterialTheme.typography.labelSmall, color = Red600)
            }
            IconButton(onClick = onEdit, enabled = !isDeleting) {
                Icon(Icons.Filled.Edit, contentDescription = "Modifier")
            }
            IconButton(onClick = onDelete, enabled = !isDeleting) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = Red600)
                }
            }
        }
    }
}

@Composable
fun UserFormDialog(
    formState: UserFormUiState,
    onFirstNameChanged: (String) -> Unit,
    onLastNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRoleChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (formState.isEditing) "Modifier l'utilisateur" else "Nouvel utilisateur",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = formState.firstName,
                    onValueChange = onFirstNameChanged,
                    label = { Text("Prénom *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = formState.lastName,
                    onValueChange = onLastNameChanged,
                    label = { Text("Nom *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = formState.email,
                    onValueChange = onEmailChanged,
                    label = { Text("Email *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (!formState.isEditing) {
                    OutlinedTextField(
                        value = formState.password,
                        onValueChange = onPasswordChanged,
                        label = { Text("Mot de passe *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                // Role selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = formState.role == "ADMIN",
                        onClick = { onRoleChanged("ADMIN") },
                        label = { Text("Admin") }
                    )
                    FilterChip(
                        selected = formState.role == "CASHIER",
                        onClick = { onRoleChanged("CASHIER") },
                        label = { Text("Caissier") }
                    )
                }

                if (formState.error != null) {
                    Text(text = formState.error!!, color = Red600, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !formState.isLoading
            ) {
                if (formState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Enregistrer")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
