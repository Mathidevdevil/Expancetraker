package com.spendwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.*
import com.spendwise.app.ui.auth.AuthScreen
import com.spendwise.app.ui.auth.AuthViewModel
import com.spendwise.app.ui.budgets.BudgetsScreen
import com.spendwise.app.ui.budgets.BudgetsViewModel
import com.spendwise.app.ui.categories.CategoriesScreen
import com.spendwise.app.ui.categories.CategoriesViewModel
import com.spendwise.app.ui.components.QuickAddBottomSheet
import com.spendwise.app.ui.dashboard.DashboardScreen
import com.spendwise.app.ui.dashboard.DashboardViewModel
import com.spendwise.app.ui.reports.ReportsScreen
import com.spendwise.app.ui.reports.ReportsViewModel
import com.spendwise.app.ui.settings.SettingsScreen
import com.spendwise.app.ui.settings.SettingsViewModel
import com.spendwise.app.ui.theme.*
import com.spendwise.app.ui.transactions.TransactionsScreen
import com.spendwise.app.ui.transactions.TransactionsViewModel
import com.spendwise.app.ui.trips.TripsScreen
import com.spendwise.app.ui.trips.TripsViewModel
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Overview", Icons.Default.Home)
    object Transactions : Screen("transactions", "Activity", Icons.Default.SwapHoriz)
    object Budgets : Screen("budgets", "Budgets", Icons.Default.PieChart)
    object Trips : Screen("trips", "Travel", Icons.Default.Flight)
    object Categories : Screen("categories", "Categories", Icons.Default.Sell)
    object Reports : Screen("reports", "Analytics", Icons.Default.BarChart)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Auth : Screen("auth", "Auth", Icons.Default.Lock)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val themeMode by dashboardViewModel.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemDark
            }
            SpendwiseTheme(darkTheme = isDark) {
                MainAppScaffold(dashboardViewModel = dashboardViewModel, isDark = isDark)
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    dashboardViewModel: DashboardViewModel,
    isDark: Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var isQuickAddOpen by remember { mutableStateOf(false) }
    val dashState by dashboardViewModel.uiState.collectAsState()

    val bottomNavItems = listOf(
        Screen.Dashboard,
        Screen.Transactions,
        Screen.Budgets,
        Screen.Trips,
        Screen.Categories
    )

    val showBottomBar = currentRoute != Screen.Auth.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    screen.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // Strictly render FAB on Dashboard so it never obstructs content on other screens
            if (showBottomBar && currentRoute == Screen.Dashboard.route) {
                FloatingActionButton(
                    onClick = { isQuickAddOpen = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Quick Add Transaction")
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Auth.route) {
                val vm: AuthViewModel = hiltViewModel()
                AuthScreen(
                    viewModel = vm,
                    onAuthSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToTransactions = { navController.navigate(Screen.Transactions.route) },
                    onNavigateToBudgets = { navController.navigate(Screen.Budgets.route) },
                    onNavigateToTrips = { navController.navigate(Screen.Trips.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    isDark = isDark,
                    onToggleTheme = {
                        dashboardViewModel.setThemeMode(if (isDark) "light" else "dark")
                    }
                )
            }
            composable(Screen.Transactions.route) {
                val vm: TransactionsViewModel = hiltViewModel()
                TransactionsScreen(
                    viewModel = vm,
                    onOpenQuickAdd = { isQuickAddOpen = true }
                )
            }
            composable(Screen.Budgets.route) {
                val vm: BudgetsViewModel = hiltViewModel()
                BudgetsScreen(viewModel = vm)
            }
            composable(Screen.Trips.route) {
                val vm: TripsViewModel = hiltViewModel()
                TripsScreen(viewModel = vm)
            }
            composable(Screen.Categories.route) {
                val vm: CategoriesViewModel = hiltViewModel()
                CategoriesScreen(viewModel = vm)
            }
            composable(Screen.Reports.route) {
                val vm: ReportsViewModel = hiltViewModel()
                ReportsScreen(viewModel = vm)
            }
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        if (isQuickAddOpen) {
            QuickAddBottomSheet(
                onDismiss = { isQuickAddOpen = false },
                categories = dashState.categories,
                trips = dashState.activeTrips,
                currencySymbol = dashState.currencySymbol,
                onSave = { amount, categoryId, tripId, paymentMethod, note, type ->
                    dashboardViewModel.quickAddExpense(amount, categoryId, tripId, paymentMethod, note, type)
                    isQuickAddOpen = false
                }
            )
        }
    }
}
