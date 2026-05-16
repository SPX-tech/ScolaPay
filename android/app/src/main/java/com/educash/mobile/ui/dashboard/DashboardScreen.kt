package com.educash.mobile.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.educash.mobile.data.model.DashboardSummary
import com.educash.mobile.data.model.MonthlyReport
import com.educash.mobile.ui.theme.*
import com.educash.mobile.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToStudents: () -> Unit = {},
    onNavigateToLedger: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edu-Cash", fontWeight = FontWeight.Bold)
                        if (uiState.userName.isNotEmpty()) {
                            Text(
                                text = uiState.userName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Welcome
                item {
                    Text(
                        text = "Tableau de bord",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // KPI Cards
                uiState.summary?.let { summary ->
                    item {
                        KpiGrid(summary)
                    }
                }

                // Quick Actions
                item {
                    Text(
                        text = "Actions rapides",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionButton(
                            title = "Nouvel élève",
                            icon = Icons.Filled.PersonAdd,
                            color = Blue700,
                            onClick = onNavigateToStudents,
                            modifier = Modifier.weight(1f)
                        )
                        ActionButton(
                            title = "Encaissement",
                            icon = Icons.Filled.Payments,
                            color = Green600,
                            onClick = onNavigateToLedger,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Monthly Report
                uiState.monthlyReport?.let { report ->
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Rapport mensuel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    item {
                        MonthlyReportCard(report)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun KpiGrid(summary: DashboardSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Total encaissé",
                value = CurrencyFormatter.format(summary.totalRevenue),
                icon = Icons.Filled.TrendingUp,
                color = Green600,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Élèves actifs",
                value = "${summary.activeStudents}",
                icon = Icons.Filled.People,
                color = Blue700,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Transactions",
                value = "${summary.totalTransactions}",
                icon = Icons.Filled.Receipt,
                color = Orange600,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Dettes",
                value = CurrencyFormatter.format(summary.totalDebts),
                icon = Icons.Filled.Warning,
                color = Red600,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = color.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MonthlyReportCard(report: MonthlyReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Revenu total", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = CurrencyFormatter.format(report.totalRevenue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Green600
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Transactions", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${report.totalTransactions}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Moyenne par transaction", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = CurrencyFormatter.format(report.averageTransaction),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
