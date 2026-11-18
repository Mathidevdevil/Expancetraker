package com.spendwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.spendwise.app.ui.components.UpdateBottomSheet
import com.spendwise.app.ui.components.glass.AuroraBackground
import com.spendwise.app.ui.components.glass.GlassFloatingAddButton
import com.spendwise.app.ui.components.glass.LiquidGlassBottomBar
import com.spendwise.app.ui.components.glass.LiquidTabItem
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
    object Dashboard : Screen("dashboard", "Overview", Icons.Default.AccountBalanceWallet)
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
    val availableUpdate by dashboardViewModel.availableUpdate.collectAsState()
    val updateDownloadState by dashboardViewModel.updateDownloadState.collectAsState()

    // 4 Streamlined Tabs: Overview, Activity, Budgets, Travel
    // Categories is cleanly relocated into Settings (accessed from top profile avatar)
    val bottomTabs = listOf(
        LiquidTabItem(Screen.Dashboard.route, "Overview", Icons.Default.AccountBalanceWallet),
        LiquidTabItem(Screen.Transactions.route, "Activity", Icons.Default.SwapHoriz),
        LiquidTabItem(Screen.Budgets.route, "Budgets", Icons.Default.PieChart),
        LiquidTabItem(Screen.Trips.route, "Travel", Icons.Default.Flight)
    )

    val activeRoute = currentRoute ?: Screen.Dashboard.route
    val isMainTabScreen = activeRoute in listOf(
        Screen.Dashboard.route,
        Screen.Transactions.route,
        Screen.Budgets.route,
        Screen.Trips.route
    )
    val showBottomBar = activeRoute != Screen.Auth.route && isMainTabScreen

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Living Aurora Chromatic Backdrop
        AuroraBackground(isDark = isDark) {
            val startDestination = if (dashboardViewModel.isLoggedIn) Screen.Dashboard.route else Screen.Auth.route

            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Auth.route) {
                    val vm: AuthViewModel = hiltViewModel()
                    AuthScreen(
                        viewModel = vm,
                        onAuthSuccess = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Auth.route) { inclusive = true }
                            }
                        },
                        isDark = isDark
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
                        onOpenQuickAdd = { isQuickAddOpen = true },
                        isDark = isDark
                    )
                }
                composable(Screen.Budgets.route) {
                    val vm: BudgetsViewModel = hiltViewModel()
                    BudgetsScreen(
                        viewModel = vm,
                        isDark = isDark
                    )
                }
                composable(Screen.Trips.route) {
                    val vm: TripsViewModel = hiltViewModel()
                    TripsScreen(
                        viewModel = vm,
                        isDark = isDark
                    )
                }
                composable(Screen.Categories.route) {
                    val vm: CategoriesViewModel = hiltViewModel()
                    CategoriesScreen(
                        viewModel = vm,
                        isDark = isDark
                    )
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
                        onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                        isDark = isDark,
                        onLogout = {
                            dashboardViewModel.logout()
                            navController.navigate(Screen.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }

        // 2. Liquid Glass Floating Bottom Bar & Floating Add FAB
        if (showBottomBar) {
            // Ambient gradient vignette so scrolling list items dissolve smoothly before the floating bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                if (isDark) Color(0x99090A12) else Color(0x99F8FAFC),
                                if (isDark) Color(0xF2090A12) else Color(0xF5F8FAFC)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassBottomBar(
                    tabs = bottomTabs,
                    currentRoute = activeRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    isDark = isDark
                )

                GlassFloatingAddButton(
                    onClick = { isQuickAddOpen = true },
                    isDark = isDark
                )
            }
        }

        // 3. Quick Add Transaction Bottom Sheet
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

        // 4. Over-The-Air In-App Auto Update Bottom Sheet
        if (availableUpdate != null) {
            UpdateBottomSheet(
                updateInfo = availableUpdate!!,
                downloadState = updateDownloadState,
                isDark = isDark,
                onStartDownload = {
                    dashboardViewModel.startUpdateDownload()
                },
                onInstall = {
                    dashboardViewModel.startUpdateDownload()
                },
                onDismiss = {
                    dashboardViewModel.dismissUpdate()
                }
            )
        }
    }
}
