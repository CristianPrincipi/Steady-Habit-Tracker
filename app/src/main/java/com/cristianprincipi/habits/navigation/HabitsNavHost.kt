package com.cristianprincipi.habits.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import com.cristianprincipi.habits.feature.history.MonthScreen
import com.cristianprincipi.habits.feature.history.YearScreen
import com.cristianprincipi.habits.feature.managehabits.ManageHabitsScreen
import com.cristianprincipi.habits.feature.notionsetup.EditMappingScreen
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupScreen
import com.cristianprincipi.habits.feature.settings.SettingsScreen
import com.cristianprincipi.habits.feature.stats.StatsScreen
import com.cristianprincipi.habits.feature.syncstatus.SyncStatusScreen
import com.cristianprincipi.habits.feature.week.WeekScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The app's shell: a setup gate, then the five-tab graph.
 *
 * **Setup is a gate around the graph, not a destination inside it** (M12.9's rule, ADR-061). An
 * unconfigured app has no habits to show in any tab, so there is nothing to navigate between and no
 * bar worth drawing. When setup finishes, [HabitsNavHostViewModel.isConfigured] flips and the whole
 * shell replaces the wizard.
 *
 * **The bar is drawn only on a [TopLevelRoute].** Sync status and the habit list are destinations
 * without tabs, and a bar with nothing selected would be a bar lying about where the user is.
 */
@Composable
fun HabitsNavHost(
    modifier: Modifier = Modifier,
    viewModel: HabitsNavHostViewModel = hiltViewModel(),
) {
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()

    HabitsShell(isConfigured = isConfigured, modifier = modifier)
}

/**
 * The shell with its one decision handed in — `CLAUDE.md` §6's stateful/stateless split.
 *
 * Splitting this out is not ceremony. Every destination below builds its own ViewModel, so the graph
 * can only be exercised inside a Hilt activity; what a test cannot reasonably arrange is the
 * *gate*, which reads a DataStore the app has already opened and which allows one instance per file
 * (ADR-032). Taking `isConfigured` as a parameter is what lets `HabitsShellNavigationTest` drive the
 * real graph without seeding a mapping onto the device first.
 */
@Composable
fun HabitsShell(
    isConfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isConfigured) {
        NotionSetupScreen(modifier = modifier)
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedTab =
        backStackEntry?.destination?.let { destination ->
            HabitsTab.entries.firstOrNull { tab -> destination.hasRoute(tab.route::class) }
        }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (selectedTab != null) {
                HabitsNavigationBar(
                    selected = selectedTab,
                    onSelect = { tab -> navController.switchTab(tab.route) },
                )
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            // Week, not Month — the user's decision. Today is one tap away on open, which is how
            // v1 gets a "today" experience without a sixth tab (`docs/design-spec.md` §1).
            startDestination = Week,
            modifier = Modifier.padding(contentPadding),
        ) {
            // ⭐ Every screen below is now real. M5.3 put a `StubScreen` on all five tabs and each
            // milestone replaced its own in place; Stats was the last, in M9.1. `StubScreen` itself
            // stays — it is where the "the shell owns insets, a screen takes the padding it is
            // handed" rule is written down, and three files cite it by name.
            composable<Month> {
                MonthScreen(onOpenSyncStatus = { navController.navigate(SyncStatus) })
            }
            // Week reaches sync status for one reason: a first import that failed leaves the
            // landing tab empty and the wizard that would have said so already gone (ADR-072).
            composable<Week> {
                WeekScreen(onOpenSyncStatus = { navController.navigate(SyncStatus) })
            }
            composable<Year> {
                YearScreen(onOpenSyncStatus = { navController.navigate(SyncStatus) })
            }
            composable<Stats> {
                StatsScreen(onOpenSyncStatus = { navController.navigate(SyncStatus) })
            }
            composable<Settings> {
                SettingsScreen(
                    onOpenSyncStatus = { navController.navigate(SyncStatus) },
                    onOpenHabits = { navController.navigate(ManageHabits) },
                )
            }
            composable<SyncStatus> {
                SyncStatusScreen(onEditMapping = { navController.navigate(EditMapping) })
            }
            // ⭐ Reachable since M7.5, from Settings — the user's choice over a glyph on the Week
            // header. The `+` this once waited on must never exist: ADR-010 makes a habit a column
            // in the user's Notion database, so a create button would be a control that cannot work.
            composable<ManageHabits> {
                ManageHabitsScreen(onEditMapping = { navController.navigate(EditMapping) })
            }
            // ⭐ M7.8, and the last step in M7. Reached from the habit list (adopt a column you just
            // added in Notion) and from sync status (a deleted column has halted the sync and this
            // is the only thing that restarts it). Saving pops straight back to whichever it was —
            // the list behind is already showing the new set, which the user chose over a
            // confirmation screen.
            composable<EditMapping> {
                EditMappingScreen(onSaved = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Switches tabs without stacking them.
 *
 * `popUpTo` the start destination keeps the back stack one deep, so system back always leaves from
 * Week rather than walking every tab the user has visited — the behaviour `docs/design-spec.md` §1
 * describes and what M5.4 verifies. `saveState`/`restoreState` mean a tab remembers its scroll
 * position, and `launchSingleTop` stops a second tap on the current tab from re-adding it.
 */
private fun NavHostController.switchTab(route: TopLevelRoute) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Whether setup has finished, and nothing else.
 *
 * ⚠ **Not "is a token stored".** The wizard saves the token at its *first* step, so routing on that
 * would swap the setup flow away the instant the user pressed Continue and they would never see the
 * database picker or the mapping (ADR-052 — found by looking at the running app, `CLAUDE.md` §7).
 *
 * Starts `false`, so a cold start shows the wizard for the few milliseconds the DataStore read
 * takes. That is the right direction to be wrong in: an unconfigured app belongs there anyway.
 */
@HiltViewModel
class HabitsNavHostViewModel
    @Inject
    constructor(
        connection: NotionSetupRepository,
    ) : ViewModel() {
        val isConfigured: StateFlow<Boolean> =
            connection.isConfigured.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = false,
            )

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
