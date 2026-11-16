package app.pocketos.board.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketos.board.ui.theme.board
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import app.pocketos.board.ui.evening.EveningScreen
import app.pocketos.board.ui.home.AttentionScreen
import app.pocketos.board.ui.home.HomeScreen
import app.pocketos.board.ui.search.SearchScreen
import app.pocketos.board.ui.chat.ChatScreen
import app.pocketos.board.domain.NavTarget
import app.pocketos.board.ui.settings.SettingsScreen
import app.pocketos.board.ui.inbox.InboxScreen
import app.pocketos.board.ui.tags.TagScreen
import app.pocketos.board.ui.trash.TrashScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: Int, val icon: Int) {
    Home("home", R.string.tab_home, R.drawable.ic_tab_home),
    Calendar("calendar", R.string.tab_calendar, R.drawable.ic_cal),
    Customers("customers", R.string.tab_customers, R.drawable.ic_tab_people),
}

private const val CUSTOMER = "customer/{id}"
private const val CASE = "case/{id}"
private const val TRASH = "trash"
private const val INBOX = "inbox"
private const val ATTENTION = "attention"
private const val EVENING = "evening"
private const val SEARCH = "search?q={q}"
private const val CHAT = "chat"
private const val SETTINGS = "settings"
private const val TAG = "tag/{name}"

@HiltViewModel
class RootViewModel @Inject constructor(
    val messenger: UiMessenger,
    val calendarTarget: app.pocketos.board.ui.calendar.CalendarTarget,
    val launch: LaunchRequests,
) : ViewModel()

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

    // اعلان، ویجت و اشتراک: مقصد را باز می‌کنند.
    LaunchedEffect(Unit) {
        root.launch.route.collect { r ->
            if (r != null) {
                val dest = when (r) {
                    "home" -> Tab.Home.route
                    "calendar" -> Tab.Calendar.route
                    "customers" -> Tab.Customers.route
                    "evening" -> EVENING
                    "settings" -> SETTINGS
                    else -> null
                }
                if (dest != null) {
                    nav.navigate(dest) {
                        if (dest == Tab.Home.route || dest == Tab.Calendar.route || dest == Tab.Customers.route) popUpTo(nav.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                }
                root.launch.route.value = null
            }
        }
    }

    // صفحه‌های زیرمجموعهٔ «مشتری‌ها» تب مشتری‌ها را روشن نگه می‌دارند.
    val inCustomers = current == Tab.Customers.route || current == CUSTOMER || current == CASE || current == TRASH || current == INBOX || current == TAG

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                HorizontalDivider(color = MaterialTheme.board.line)
                Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Tab.entries.forEach { tab ->
                        val on = when (tab) {
                            Tab.Customers -> inCustomers
                            Tab.Home -> current == Tab.Home.route || current == ATTENTION || current == EVENING || current == SEARCH || current == SETTINGS || current == CHAT
                            else -> current == tab.route
                        }
                        val color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.board.muted
                        Column(
                            Modifier.weight(1f).clickable {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = tab != Tab.Customers
                                }
                            }.padding(top = 7.dp, bottom = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                            Text(stringResource(tab.label), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal)
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onOpenCustomer = { nav.navigate("customer/$it") },
                    onOpenCase = { nav.navigate("case/$it") },
                    onOpenTag = { nav.navigate("tag/" + Uri.encode(it)) },
                    onOpenAttention = { nav.navigate(ATTENTION) },
                    onOpenCalendar = { nav.navigate(Tab.Calendar.route) { launchSingleTop = true } },
                    onOpenTomorrow = { day ->
                        root.calendarTarget.request.value = day
                        nav.navigate(Tab.Calendar.route) { launchSingleTop = true }
                    },
                    onOpenInbox = { nav.navigate(INBOX) },
                    onOpenEvening = { nav.navigate(EVENING) },
                    onOpenSearch = { nav.navigate("search?q=") },
                    onOpenChat = { nav.navigate(CHAT) },
                    onOpenSettings = { nav.navigate(SETTINGS) },
                )
            }
            composable(ATTENTION) {
                AttentionScreen(
                    onBack = { nav.popBackStack() }, onOpenCustomer = { nav.navigate("customer/$it") },
                    onOpenCase = { nav.navigate("case/$it") }, onOpenTag = { nav.navigate("tag/" + Uri.encode(it)) },
                )
            }
            composable(CHAT) {
                ChatScreen(
                    onBack = { nav.popBackStack() },
                    onNavigate = { t ->
                        when (t) {
                            is NavTarget.Case -> nav.navigate("case/${t.id}")
                            is NavTarget.Customer -> nav.navigate("customer/${t.id}")
                            NavTarget.Inbox -> nav.navigate(INBOX)
                            is NavTarget.Tag -> nav.navigate("tag/" + Uri.encode(t.tag))
                            is NavTarget.Search -> nav.navigate("search?q=" + Uri.encode(t.q))
                        }
                    },
                )
            }
            composable(SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onOpenTrash = { nav.navigate(TRASH) }) }
            composable(EVENING) { EveningScreen(onBack = { nav.popBackStack() }) }
            composable(SEARCH, arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) {
                SearchScreen(
                    onBack = { nav.popBackStack() }, onOpenCustomer = { nav.navigate("customer/$it") },
                    onOpenCase = { nav.navigate("case/$it") }, onOpenTag = { nav.navigate("tag/" + Uri.encode(it)) },
                )
            }
            composable(Tab.Calendar.route) {
                CalendarScreen(onOpenCustomer = { nav.navigate("customer/$it") }, onOpenCase = { nav.navigate("case/$it") })
            }
            composable(Tab.Customers.route) {
                CustomersScreen(
                    onOpenCustomer = { nav.navigate("customer/$it") },
                    onOpenCase = { nav.navigate("case/$it") },
                    onOpenTrash = { nav.navigate(TRASH) },
                    onOpenInbox = { nav.navigate(INBOX) },
                )
            }
            composable(CUSTOMER, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                CustomerScreen(
                    onBack = { nav.popBackStack() }, onOpenCase = { nav.navigate("case/$it") },
                    onOpenTag = { nav.navigate("tag/" + Uri.encode(it)) },
                )
            }
            composable(CASE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                CaseScreen(
                    onBack = { nav.popBackStack() }, onOpenCustomer = { nav.navigate("customer/$it") },
                    onOpenTag = { nav.navigate("tag/" + Uri.encode(it)) },
                )
            }
            composable(TRASH) { TrashScreen(onBack = { nav.popBackStack() }) }
            composable(INBOX) { InboxScreen(onBack = { nav.popBackStack() }, onOpenTag = { nav.navigate("tag/" + Uri.encode(it)) }) }
            composable(TAG, arguments = listOf(navArgument("name") { type = NavType.StringType })) {
                TagScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
