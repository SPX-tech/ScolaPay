package com.scolapay.mobile.ui.transactions

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.scolapay.mobile.data.model.Transaction
import com.scolapay.mobile.ui.theme.*
import com.scolapay.mobile.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingVoid by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }

    pendingVoid?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingVoid = null },
            title = { Text("Annuler l'encaissement") },
            text = { OutlinedTextField(value = voidReason, onValueChange = { voidReason = it }, label = { Text("Motif") }) },
            confirmButton = { TextButton(enabled = voidReason.trim().length >= 5, onClick = { viewModel.voidTransaction(id, voidReason); pendingVoid = null }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { pendingVoid = null }) { Text("Retour") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Filter chips
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedStatus == null,
                    onClick = { viewModel.onStatusFilterChanged(null) },
                    label = { Text("Toutes") }
                )
                FilterChip(
                    selected = uiState.selectedStatus == "POSTED",
                    onClick = { viewModel.onStatusFilterChanged("POSTED") },
                    label = { Text("Validées") }
                )
                FilterChip(
                    selected = uiState.selectedStatus == "VOIDED",
                    onClick = { viewModel.onStatusFilterChanged("VOIDED") },
                    label = { Text("Annulées") }
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Receipt, contentDescription = null,
                            modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Aucune transaction", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.transactions) { transaction ->
                        TransactionCard(
                            transaction = transaction,
                            onVoid = { voidReason = ""; pendingVoid = transaction.id },
                            isVoiding = uiState.voidingTransactionId == transaction.id
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionCard(
    transaction: Transaction,
    onVoid: () -> Unit,
    isVoiding: Boolean
) {
    val isVoid = transaction.status == "VOIDED"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isVoid) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when {
                    isVoid -> Red50
                    else -> Green50
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isVoid) Icons.Filled.Cancel else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (isVoid) Red600 else Green600,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${transaction.student?.firstName ?: ""} ${transaction.student?.lastName ?: ""}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = transaction.description ?: "Paiement ${transaction.paymentMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = transaction.createdAt?.substringBefore("T") ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.format(transaction.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isVoid) MaterialTheme.colorScheme.onSurfaceVariant else Green600
                )
                if (transaction.paymentMethod != null) {
                    Text(
                        text = transaction.paymentMethod,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isVoid) {
                    TextButton(
                        onClick = onVoid,
                        enabled = !isVoiding,
                        colors = ButtonDefaults.textButtonColors(contentColor = Red600)
                    ) {
                        if (isVoiding) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.dp)
                        } else {
                            Text("Annuler", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
