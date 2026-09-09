package com.cristianprincipi.habits.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import com.cristianprincipi.habits.feature.about.AboutScreen
import com.cristianprincipi.habits.feature.history.MonthScreen
import com.cristianprincipi.habits.feature.history.YearScreen
import com.cristianprincipi.habits.feature.managehabits.ManageHabitsScreen
import com.cristianprincipi.habits.feature.notionsetup.EditMappingScreen
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupScreen
import com.cristianprincipi.habits.feature.notionsetup.ReplaceTokenScreen
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
 *
 * **The graph moves two ways, and they are told apart by [isLateral]** — M12.5. Switching tabs is
 * instant; pushing a destination slides. See that function for why the app had a 700ms animation
 * nobody had chosen.
 */
@Composable
fun HabitsNavHost(
    modifier: Modifier = Modifier,
    landing: TopLevelRoute? = null,
    onLandingHandled: () -> Unit = {},
    viewModel: HabitsNavHostViewModel = hiltViewModel(),
) {
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()

    HabitsShell(
        isConfigured = isConfigured,
        modifier = modifier,
        landing = landing,
        onLandingHandled = onLandingHandled,
    )
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
    landing: TopLevelRoute? = null,
    onLandingHandled: () -> Unit = {},
) {
    if (!isConfigured) {
        NotionSetupScreen(modifier = modifier)
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedTab = backStackEntry?.destination?.tab()

    // ⭐ M15.8. A home-screen widget asks for Week, and this is where the asking lands.
    //
    // ⚠ It is [switchTab] rather than a bare navigate, which is what makes the request work from a
    // PUSHED destination too: `popUpTo` the start destination clears About or the habit list on the
    // way, so the tap ends on Week with nothing stacked over it. From Week itself it is a no-op,
    // because `launchSingleTop` refuses to re-add what is already on top.
    //
    // ⚠ An unconfigured app never reaches this line — the gate above returns first — so a tap
    // during setup is simply dropped, which is right: there is one screen and it is not a tab.
    LaunchedEffect(landing) {
        if (landing != null) {
            navController.switchTab(landing)
            onLandingHandled()
        }
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
            // ⭐ M12.5. All four are given, because leaving any one out restores the 700ms default
            // for that direction alone — a back gesture that dissolves where the forward one slid.
            //
            // ⚠⚠ M15.1: the two `predictive*` ones are NOT decoration, and they are why the user
            // saw the outgoing page "in small" for half a second. navigation-compose 2.10.0 added
            // them (2.9.8 has no such parameter — read from both artifacts, not remembered) and
            // **they, not `popEnter`/`popExit`, are what runs while a finger is on the screen**.
            // Left unset they take the library's own preview: the outgoing screen shrinks to a
            // card and sits there until the gesture commits. Setting them is what makes a
            // gesture-driven back match the back it is a gesture for.
            enterTransition = { if (isLateral()) EnterTransition.None else slideIntoContainer(Forward) },
            exitTransition = { if (isLateral()) ExitTransition.None else slideOutOfContainer(Forward) },
            popEnterTransition = { if (isLateral()) EnterTransition.None else slideIntoContainer(Back) },
            popExitTransition = { if (isLateral()) ExitTransition.None else slideOutOfContainer(Back) },
            predictivePopEnterTransition = { if (isLateral()) EnterTransition.None else slideIntoContainer(Back) },
            predictivePopExitTransition = { if (isLateral()) ExitTransition.None else slideOutOfContainer(Back) },
        ) {
            // ⭐ Every screen below is now real. M5.3 put a `StubScreen` on all five tabs and each
            // milestone replaced its own in place; Stats was the last, in M9.1. `StubScreen` itself
            // stays — it is where the "the shell owns insets, a screen takes the padding it is
            // handed" rule is written down, and three files cite it by name.
            screen<Month> {
                MonthScreen(onOpenSyncStatus = { navController.navigate(SyncStatus) })
            }
            // Week reaches sync status for one reason: a first import that failed leaves the
            // landing tab empty and the wizard that would have said so already gone (ADR-072).
            screen<Week> {
                WeekScreen(onOpenSyncStatus = { navController.navigate(SyncStatus) })
            }
            screen<Year> {
                YearScreen(onOpenSyncStatus = { navController.navigate(SyncStatus) })
            }
            screen<Stats> {
                StatsScreen(onOpenSyncStatus = { navController.navigate(SyncStatus) })
            }
            screen<Settings> {
                SettingsScreen(
                    onOpenSyncStatus = { navController.navigate(SyncStatus) },
                    onOpenHabits = { navController.navigate(ManageHabits) },
                    onOpenAbout = { navController.navigate(About) },
                )
            }
            screen<SyncStatus> {
                SyncStatusScreen(
                    onEditMapping = { navController.navigate(EditMapping) },
                    onReplaceToken = { navController.navigate(ReplaceToken) },
                )
            }
            // ⭐ M12.7. `popBackStack` rather than a navigate: the user came here from the sync
            // screen to fix one thing, and when it is fixed the screen behind them is the one that
            // told them it was broken - now showing that it is not.
            screen<ReplaceToken> {
                ReplaceTokenScreen(onSaved = { navController.popBackStack() })
            }
            // ⭐ M11.8, and the last live step in M11. A destination rather than a tab, reached
            // from the Settings hub like the sync screen above it, and it takes nothing: everything
            // on it is fixed at build time.
            screen<About> {
                AboutScreen()
            }
            // ⭐ Reachable since M7.5, from Settings — the user's choice over a glyph on the Week
            // header. The `+` this once waited on must never exist: ADR-010 makes a habit a column
            // in the user's Notion database, so a create button would be a control that cannot work.
            screen<ManageHabits> {
                ManageHabitsScreen(onEditMapping = { navController.navigate(EditMapping) })
            }
            // ⭐ M7.8, and the last step in M7. Reached from the habit list (adopt a column you just
            // added in Notion) and from sync status (a deleted column has halted the sync and this
            // is the only thing that restarts it). Saving pops straight back to whichever it was —
            // the list behind is already showing the new set, which the user chose over a
            // confirmation screen.
            screen<EditMapping> {
                EditMappingScreen(onSaved = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Registers one destination, on its own opaque background.
 *
 * ### ⚠⚠ Every screen used to be transparent, and a back gesture showed two of them at once
 *
 * M15.1, and the user's report was exact: *"when I swipe back to return to Week, there is a delay
 * that causes ghosting — I can see the previous page for a second."* Reproduced on the emulator at
 * two points of a slow drag: **Week's title, its "Sep 7 – Sep 13" header, its day-name row and its
 * pills were all legible through Statistics' chips, tiles and chart**, with both bottom-bar items
 * lit at once — Week green and Stats purple, each with its own dot.
 *
 * The cause is not a transition. [HabitsShell]'s `Scaffold` paints `colorScheme.background` **once,
 * behind the whole `NavHost`**, and no screen paints anything behind itself — so the two entries
 * that a transition necessarily composes at the same time composite onto that one fill, and onto
 * each other. Nothing was ghosting; both pages were simply being drawn.
 *
 * ### ⚠ Which is why this is not a fix to [isLateral]'s transitions, and must not become one
 *
 * The same double exposure was measured on the **push** path — About over Settings, where
 * `popExitTransition` is a real `slideOutOfContainer` — as on the lateral path, where both
 * transitions are `None`. A defect that survives both settings of the thing you suspect is not
 * caused by it. `EnterTransition.None` on a tab switch stays exactly as M12.5 left it (ADR-105).
 *
 * ⭐ **It is a `screen<T>` rather than a `Box` inside each of the ten bodies** for the reason M15.4
 * gives for keeping measures out of `habitPropertyIds`: an eleventh destination added through the
 * only registration function there is cannot forget to be opaque, and a rule that cannot be
 * forgotten is worth more than one that is merely written down.
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(crossinline content: @Composable () -> Unit) =
    composable<T> {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }

/**
 * Which tab this destination is, or `null` for one of the destinations that has no tab.
 *
 * Used twice and for two different jobs — deciding whether to draw the bar, and deciding how the
 * graph should move — which is the whole reason it is a function rather than an expression written
 * out at both sites. The two answers must not be able to disagree.
 */
private fun NavDestination.tab(): HabitsTab? = HabitsTab.entries.firstOrNull { tab -> hasRoute(tab.route::class) }

/**
 * Whether this is a move **between tabs** rather than into or out of a pushed destination.
 *
 * ### ⚠⚠ The app had a 700ms cross-fade, and nobody had chosen it
 *
 * `NavHost`'s own defaults are `fadeIn(tween(700))` and `fadeOut(tween(700))` — read out of
 * navigation-compose 2.9.8's source, not remembered. So every tab switch dissolved for seven tenths
 * of a second: **4.7× the 150ms the design gives everything else**, on the control the user taps
 * most. M12.5's audit had recorded "no screen transition exists", which is how a default hides — it
 * is not in our code, so a search of our code does not find it.
 *
 * ### The two moves are not the same move
 *
 * A tab switch is **lateral**: five peers, a bar that says where you are, and a back stack kept one
 * deep by [switchTab] so it is not a journey to retrace. The prototype agrees — its `setView` is a
 * plain re-render with no transition at all, and what marks the change is the nav item's own colour.
 * So: instant, which is both the faithful port and the user's ruling (2026-09-05).
 *
 * Everything else — sync status, about, the habit list, the two repair screens — is a **push**: one
 * screen deeper, reached from a row you tapped, left by going back. Those are undesigned
 * (`docs/design-spec.md` §8 lists no transitions at all), so `CLAUDE.md` §2's rule applies and they
 * take the platform's convention: slide in from the end, slide back out the way they came, at
 * `slideIntoContainer`'s own default spring rather than a duration invented here.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isLateral(): Boolean =
    initialState.destination.tab() != null && targetState.destination.tab() != null

private val Forward = AnimatedContentTransitionScope.SlideDirection.Start
private val Back = AnimatedContentTransitionScope.SlideDirection.End

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
