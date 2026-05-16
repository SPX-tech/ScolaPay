package com.educash.mobile.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.educash.mobile.ui.dashboard.DashboardScreen
import com.educash.mobile.ui.ledger.LedgerScreen
import com.educash.mobile.ui.login.LoginScreen
import com.educash.mobile.ui.login.LoginViewModel
import com.educash.mobile.ui.reports.ReportsScreen
import com.educash.mobile.ui.settings.SettingsScreen
import com.educash.mobile.ui.students.*
import com.educash.mobile.ui.transactions.TransactionsScreen
import com.educash.mobile.ui.users.UsersScreen

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Accueil", Icons.Filled.Home, Screen.Dashboard.route),
    BottomNavItem("Élèves", Icons.Filled.People, Screen.StudentsList.route),
    BottomNavItem("Caisse", Icons.Filled.AccountBalance, Screen.Ledger.route),
    BottomNavItem("Transactions", Icons.Filled.Receipt, Screen.Transactions.route),
    BottomNavItem("Plus", Icons.Filled.MoreHoriz, Screen.Settings.route)
)

@Composable
fun AppNavigation(
    startDestination: String = Screen.Login.route
) {
    val navController = rememberNavController()
    val loginViewModel: LoginViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            MainScaffold(
                navController = navController,
                currentRoute = Screen.Dashboard.route,
                content = {
                    DashboardScreen(
                        onNavigateToStudents = {
                            navController.navigate(Screen.StudentsList.route)
                        },
                        onNavigateToLedger = {
                            navController.navigate(Screen.Ledger.route)
                        }
                    )
                }
            )
        }

        composable(Screen.StudentsList.route) {
            MainScaffold(
                navController = navController,
                currentRoute = Screen.StudentsList.route,
                content = {
                    StudentsListScreen(
                        onStudentClick = { studentId ->
                            navController.navigate(Screen.StudentDetail.createRoute(studentId))
                        },
                        onAddStudent = {
                            navController.navigate(Screen.StudentForm.createRoute())
                        }
                    )
                }
            )
        }

        composable(
            route = Screen.StudentDetail.route,
            arguments = listOf(navArgument("studentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId") ?: return@composable
            StudentDetailScreen(
                studentId = studentId,
                onNavigateBack = { navController.popBackStack() },
                onEditStudent = { id ->
                    navController.navigate(Screen.StudentForm.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.StudentForm.route,
            arguments = listOf(navArgument("studentId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId")
            StudentFormScreen(
                studentId = if (studentId != null && studentId > 0) studentId else null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Ledger.route) {
            MainScaffold(
                navController = navController,
                currentRoute = Screen.Ledger.route,
                content = { LedgerScreen() }
            )
        }

        composable(Screen.Transactions.route) {
            MainScaffold(
                navController = navController,
                currentRoute = Screen.Transactions.route,
                content = { TransactionsScreen() }
            )
        }

        composable(Screen.Users.route) {
            MainScaffold(
                navController = navController,
                currentRoute = Screen.Users.route,
                content = { UsersScreen() }
            )
        }

        composable(Screen.Reports.route) {
            MainScaffold(
                navController = navController,
                currentRoute = Screen.Reports.route,
                content = { ReportsScreen() }
            )
        }

        composable(Screen.Settings.route) {
            MainScaffold(
                navController = navController,
                currentRoute = Screen.Settings.route,
                content = {
                    SettingsScreen(
                        onLogout = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    navController: NavHostController,
    currentRoute: String,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == item.route
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
