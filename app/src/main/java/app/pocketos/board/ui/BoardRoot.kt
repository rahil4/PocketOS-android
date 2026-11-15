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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.pocketos.board.R
import app.pocketos.board.ui.calendar.CalendarScreen
import app.pocketos.board.ui.customers.CustomersScreen
import app.pocketos.board.ui.home.HomeScreen

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    Home("home", R.string.tab_home, Icons.Filled.Home),
    Calendar("calendar", R.string.tab_calendar, Icons.Filled.DateRange),
    Customers("customers", R.string.tab_customers, Icons.Filled.Person),
}

@Composable
fun BoardRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Home.route) { HomeScreen() }
            composable(Tab.Calendar.route) { CalendarScreen() }
            composable(Tab.Customers.route) { CustomersScreen() }
        }
    }
}
