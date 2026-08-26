package com.cristianprincipi.habits.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.SecondaryButton
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupScreen
import com.cristianprincipi.habits.feature.syncstatus.SyncStatusScreen
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
            // ⚠ The five tabs are empty on purpose: **M5.3 stubs every screen**, and doing it here
            // would be that step's work under this step's name. What M5.2 is answerable for is that
            // each destination exists, the bar switches between them, and the right tab lights up.
            composable<Month> { }
            composable<Week> { }
            composable<Year> { }
            composable<Stats> { }
            composable<Settings> {
                SettingsScaffolding(onOpenSyncStatus = { navController.navigate(SyncStatus) })
            }
            composable<SyncStatus> { SyncStatusScreen() }
            // ⚠ Declared but unreachable until the header's `+` exists (M6/M7.5). That is a stated
            // gap, unlike sync status, which the user asked to keep reachable from this step on.
            composable<ManageHabits> { }
        }
    }
}

/**
 * Settings, until M7 builds it: one row, into sync status.
 *
 * ⭐ **This exists because the user asked for it in M5.2.** Deleting `HabitsAppRoot` took away the
 * only thing pointing at `SyncStatusScreen` — it used to be what every configured user saw — and a
 * destination nothing navigates to is unreachable however carefully it is declared. Rather than
 * leave the Notion connection unreachable for a whole milestone, Settings carries a single row into
 * it. M7 absorbs this when it builds the real screen; there is nothing here worth keeping.
 */
@Composable
private fun SettingsScaffolding(onOpenSyncStatus: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.screenPadding),
    ) {
        SecondaryButton(
            text = stringResource(R.string.settings_notion_sync),
            onClick = onOpenSyncStatus,
        )
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
