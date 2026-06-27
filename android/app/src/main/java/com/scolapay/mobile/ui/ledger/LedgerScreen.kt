package com.scolapay.mobile.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scolapay.mobile.data.model.Student
import com.scolapay.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.success) {
        AlertDialog(
            onDismissRequest = { viewModel.resetSuccess() },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Green600) },
            title = { Text("Paiement enregistré !") },
            text = { Text("Le paiement a été enregistré avec succès.") },
            confirmButton = {
                Button(onClick = { viewModel.resetSuccess() }) {
                    Text("Nouveau paiement")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Caisse", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                item {
                    Text(
                        text = "Encaissement",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enregistrer un paiement en espèces",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Search
                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Rechercher un élève...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true
                    )
                }

                // Class filter
                if (uiState.classes.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.selectedClassId == null,
                                onClick = { viewModel.onClassFilterChanged(null) },
                                label = { Text("Tous") }
                            )
                            uiState.classes.take(4).forEach { cls ->
                                FilterChip(
                                    selected = uiState.selectedClassId == cls.id,
                                    onClick = { viewModel.onClassFilterChanged(cls.id) },
                                    label = { Text(cls.name) }
                                )
                            }
                        }
                    }
                }

                // Student list
                val filtered = viewModel.filteredStudents
                if (filtered.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sélectionner l'élève",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(filtered.take(20)) { student ->
                        StudentSelectionCard(
                            student = student,
                            isSelected = student.id == uiState.selectedStudentId,
                            onClick = { viewModel.onStudentSelected(student.id) }
                        )
                    }
                }

                // Amount input (only if student selected)
                if (uiState.selectedStudentId != null) {
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                    item {
                        OutlinedTextField(
                            value = uiState.amount,
                            onValueChange = viewModel::onAmountChanged,
                            label = { Text("Montant (FCFA)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Filled.Money, contentDescription = null) },
                            singleLine = true
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = uiState.description,
                            onValueChange = viewModel::onDescriptionChanged,
                            label = { Text("Description (optionnelle)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3
                        )
                    }

                    // Error
                    if (uiState.error != null) {
                        item {
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
                    }

                    item {
                        Button(
                            onClick = { viewModel.recordPayment() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = !uiState.isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = Green600)
                        ) {
                            if (uiState.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Payments, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enregistrer le paiement", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentSelectionCard(
    student: Student,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Blue50 else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder() else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${student.firstName} ${student.lastName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                student.className?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
