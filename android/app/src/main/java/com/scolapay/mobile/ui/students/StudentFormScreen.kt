package com.scolapay.mobile.ui.students

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scolapay.mobile.ui.theme.Blue700

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormScreen(
    studentId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: StudentsViewModel = viewModel()
) {
    LaunchedEffect(studentId) { viewModel.loadForm(studentId) }
    val uiState by viewModel.formState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (studentId != null) "Modifier l'élève" else "Nouvel élève", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Prénom
            OutlinedTextField(
                value = uiState.firstName,
                onValueChange = viewModel::onFirstNameChanged,
                label = { Text("Prénom *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Nom
            OutlinedTextField(
                value = uiState.lastName,
                onValueChange = viewModel::onLastNameChanged,
                label = { Text("Nom *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Email
            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChanged,
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Téléphone
            OutlinedTextField(
                value = uiState.phone,
                onValueChange = viewModel::onPhoneChanged,
                label = { Text("Téléphone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Matricule
            OutlinedTextField(
                value = uiState.matricule,
                onValueChange = viewModel::onMatriculeChanged,
                label = { Text("Matricule") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Classe dropdown
            if (uiState.classes.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                val selectedClass = uiState.classes.find { it.id == uiState.classId }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedClass?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Classe *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.classes.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls.name) },
                                onClick = {
                                    viewModel.onClassChanged(cls.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Error
            if (uiState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.error!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Button(
                onClick = {
                    if (studentId != null) viewModel.updateStudent(studentId)
                    else viewModel.saveStudent()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Blue700)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (studentId != null) "Enregistrer les modifications" else "Ajouter l'élève",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
