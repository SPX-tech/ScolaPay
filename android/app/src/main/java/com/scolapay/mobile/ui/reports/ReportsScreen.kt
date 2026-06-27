package com.scolapay.mobile.ui.reports

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scolapay.mobile.ui.theme.*
import com.scolapay.mobile.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rapports", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tabs
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.onTabChanged(0) },
                    text = { Text("Revenus") },
                    icon = { Icon(Icons.Filled.TrendingUp, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.onTabChanged(1) },
                    text = { Text("Dettes") },
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null) }
                )
            }

            when (uiState.selectedTab) {
                0 -> RevenueReportTab(uiState, viewModel)
                1 -> DebtsReportTab(uiState, viewModel)
            }
        }
    }
}

@Composable
fun RevenueReportTab(uiState: ReportsUiState, viewModel: ReportsViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Date range
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.startDate,
                onValueChange = viewModel::onStartDateChanged,
                label = { Text("Début") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.endDate,
                onValueChange = viewModel::onEndDateChanged,
                label = { Text("Fin") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.loadRevenueReport() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Blue700)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Générer le rapport")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.revenueReport != null) {
            val report = uiState.revenueReport!!

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Récapitulatif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Revenu total")
                        Text(CurrencyFormatter.format(report.totalRevenue), fontWeight = FontWeight.Bold, color = Green600)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Nombre de transactions")
                        Text("${report.totalTransactions}", fontWeight = FontWeight.SemiBold)
                    }

                    if (report.byPaymentMethod.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Par méthode de paiement", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        report.byPaymentMethod.forEach { (method, amount) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                                Text(method, style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.format(amount), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.BarChart, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sélectionnez une période", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DebtsReportTab(uiState: ReportsUiState, viewModel: ReportsViewModel) {
    LaunchedEffect(Unit) { viewModel.loadDebtsReport() }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (uiState.debtsReport.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null,
                    modifier = Modifier.size(64.dp), tint = Green600)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Aucune dette en cours", style = MaterialTheme.typography.bodyLarge)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "${uiState.debtsReport.size} élève(s) avec des dettes",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(uiState.debtsReport) { debt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${debt.studentFirstName} ${debt.studentLastName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            debt.className?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            text = CurrencyFormatter.format(debt.amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Red600
                        )
                    }
                }
            }
        }
    }
}
