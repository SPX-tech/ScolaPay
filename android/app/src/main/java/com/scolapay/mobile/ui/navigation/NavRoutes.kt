package com.scolapay.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    data object Login : Screen("login", "Connexion")
    data object Dashboard : Screen("dashboard", "Accueil", Icons.Filled.Home)
    data object StudentsList : Screen("students", "Élèves", Icons.Filled.People)
    data object StudentDetail : Screen("students/{studentId}", "Détail élève") {
        fun createRoute(studentId: String) = "students/$studentId"
    }
    data object StudentForm : Screen("students/form?studentId={studentId}", "Nouvel élève") {
        fun createRoute(studentId: String? = null) =
            if (studentId != null) "students/form?studentId=$studentId"
            else "students/form"
    }
    data object Ledger : Screen("ledger", "Caisse", Icons.Filled.AccountBalance)
    data object Transactions : Screen("transactions", "Transactions", Icons.Filled.Receipt)
    data object Users : Screen("users", "Utilisateurs", Icons.Filled.AdminPanelSettings)
    data object Reports : Screen("reports", "Rapports", Icons.Filled.BarChart)
    data object Settings : Screen("settings", "Paramètres", Icons.Filled.Settings)

    companion object {
        val bottomNavItems = listOf(
            Dashboard,
            StudentsList,
            Ledger,
            Transactions,
            Settings
        )
    }
}
