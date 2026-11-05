package com.scolapay.mobile.ui.students

import androidx.compose.foundation.clickable
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
import com.scolapay.mobile.data.model.Student
import com.scolapay.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsListScreen(
    onStudentClick: (String) -> Unit,
    onAddStudent: () -> Unit,
    viewModel: StudentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.listState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Élèves", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddStudent,
                containerColor = Blue700
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    viewModel.onSearchQueryChanged(it)
                },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Rechercher un élève...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""
                            viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true
            )

            // Class filter chips
            if (uiState.classes.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedClassId == null,
                        onClick = { viewModel.onClassFilterChanged(null) },
                        label = { Text("Toutes") }
                    )
                    uiState.classes.take(5).forEach { cls ->
                        FilterChip(
                            selected = uiState.selectedClassId == cls.id,
                            onClick = { viewModel.onClassFilterChanged(cls.id) },
                            label = { Text(cls.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.students.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PeopleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Aucun élève trouvé", style = MaterialTheme.typography.bodyLarge)
                        Text("Cliquez sur + pour ajouter", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.students) { student ->
                        StudentCard(student = student, onClick = { onStudentClick(student.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun StudentCard(student: Student, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Blue50,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${student.firstName.take(1)}${student.lastName.take(1)}",
                        fontWeight = FontWeight.Bold,
                        color = Blue700
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${student.firstName} ${student.lastName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                student.className?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (student.matricule != null) {
                    Text(text = "Matricule: ${student.matricule}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Status badge
            if (student.status != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (student.status == "ACTIVE") Green50 else Red50
                ) {
                    Text(
                        text = if (student.status == "ACTIVE") "Actif" else "Inactif",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (student.status == "ACTIVE") Green600 else Red600
                    )
                }
            }
        }
    }
}
