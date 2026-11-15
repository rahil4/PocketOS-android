package app.pocketos.board.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pocketos.board.R
import app.pocketos.board.ui.calendar.CalendarScreen
import app.pocketos.board.ui.cases.CaseScreen
import app.pocketos.board.ui.customers.CustomerScreen
import app.pocketos.board.ui.customers.CustomersScreen
import app.pocketos.board.ui.home.HomeScreen
import app.pocketos.board.ui.trash.TrashScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    Home("home", R.string.tab_home, Icons.Filled.Home),
    Calendar("calendar", R.string.tab_calendar, Icons.Filled.DateRange),
    Customers("customers", R.string.tab_customers, Icons.Filled.Person),
}

private const val CUSTOMER = "customer/{id}"
private const val CASE = "case/{id}"
private const val TRASH = "trash"

@HiltViewModel
class RootViewModel @Inject constructor(val messenger: UiMessenger) : ViewModel()

@Composable
fun BoardRoot(root: RootViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        root.messenger.events.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(msg.text, msg.actionLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) msg.action?.let { act -> root.messenger.scope.launch { act() } }
        }
    }

    // صفحه‌های زیرمجموعهٔ «مشتری‌ها» تب مشتری‌ها را روشن نگه می‌دارند.
    val inCustomers = current == Tab.Customers.route || current == CUSTOMER || current == CASE || current == TRASH

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = if (tab == Tab.Customers) inCustomers else current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = tab != Tab.Customers
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Home.route) { HomeScreen() }
            composable(Tab.Calendar.route) { CalendarScreen() }
            composable(Tab.Customers.route) {
                CustomersScreen(
                    onOpenCustomer = { nav.navigate("customer/$it") },
                    onOpenCase = { nav.navigate("case/$it") },
                    onOpenTrash = { nav.navigate(TRASH) },
                )
            }
            composable(CUSTOMER, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                CustomerScreen(onBack = { nav.popBackStack() }, onOpenCase = { nav.navigate("case/$it") })
            }
            composable(CASE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                CaseScreen(onBack = { nav.popBackStack() }, onOpenCustomer = { nav.navigate("customer/$it") })
            }
            composable(TRASH) { TrashScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
