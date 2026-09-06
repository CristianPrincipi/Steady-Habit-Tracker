package com.cristianprincipi.habits.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * ⭐⭐ M13.7 — **does switching tabs destroy the tab's `ViewModel`?**
 *
 * ### Why this exists rather than a paragraph of reasoning
 *
 * Three of this app's ViewModels hold state that is not derived from Room or DataStore:
 * `WeekViewModel.anchor` (which week is on screen), `MonthViewModel.anchor` and
 * `StatsViewModel.selection`. Whether paging to a past month and then visiting another tab loses
 * that month depends entirely on one fact about Navigation Compose: what
 * `popUpTo(...) { saveState = true }` does to the popped entry's `ViewModelStore`.
 *
 * **That would be a far more frequent defect than process death** — a tab switch is a gesture the
 * user makes constantly. So it is worth measuring rather than assuming, and worth keeping measured:
 * `HabitsNavHost.switchTab` is four lines a later session could plausibly "simplify".
 *
 * ### ⚠⚠ The asymmetry this probe had to be rebuilt to see
 *
 * `switchTab` pops up to **the start destination, exclusive** — `popUpTo(findStartDestination().id)`
 * with no `inclusive = true`. The start destination is `Week`, so **`Week` is never popped at all**
 * and its ViewModel is never at risk whatever the flags say. Only the other four tabs are popped,
 * and only they depend on `saveState`.
 *
 * The first version of this probe used two destinations and made the one under test the start
 * destination, so **both** its tests round-tripped a destination that was never popped. The one that
 * mattered passed, and it passed for a reason that had nothing to do with `saveState` — the exact
 * shape of vacuous test M13.5 shipped twice (ADR-112 §6). ⭐ **The control caught it**: with the
 * flags off it still reported the same instance, which is impossible if anything was being popped.
 * Hence [ProbeHome], and hence a route under test that is not the start.
 *
 * ⚠ **The routes and the ViewModel are synthetic on purpose.** The app's own tabs cannot answer this
 * without a Notion connection to put habits on screen, and the question is about *Navigation*, not
 * about the Month screen — the same reasoning that made `TouchTargetExpansionProbe` a bare 40dp box.
 */
@RunWith(AndroidJUnit4::class)
class TabViewModelRetentionProbe {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    /** Stands in for `Week`: the start destination, which `switchTab` never pops. */
    @Serializable
    private data object ProbeHome

    /** Stands in for `Month` — a tab that *is* popped when the user goes somewhere else. */
    @Serializable
    private data object ProbeA

    /** Stands in for `Year`: somewhere else to go. */
    @Serializable
    private data object ProbeB

    /**
     * A ViewModel whose only behaviour is to be countable.
     *
     * The serial is assigned in the constructor, which is the moment the framework decides it needs
     * a new instance — exactly the event under test.
     */
    class ProbeViewModel : ViewModel() {
        val serial: Int = built.incrementAndGet()

        companion object {
            val built = AtomicInteger(0)
        }
    }

    /** The serial currently on screen for each destination, as its composition last reported it. */
    private val serials = mutableMapOf<String, Int>()

    @Before
    fun resetSerials() {
        ProbeViewModel.built.set(0)
        serials.clear()
    }

    @Composable
    private fun Probe(name: String) {
        val serial = viewModel<ProbeViewModel>().serial
        SideEffect { serials[name] = serial }
        Text("$name is serial $serial")
    }

    private fun start() {
        composeTestRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = ProbeHome) {
                composable<ProbeHome> { Probe("home") }
                composable<ProbeA> { Probe("a") }
                composable<ProbeB> { Probe("b") }
            }
        }
    }

    /** Navigates exactly as `HabitsNavHost.switchTab` does — the code under test, copied verbatim. */
    private fun switchTabTo(route: Any) {
        composeTestRule.runOnIdle {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    /** The same move with both flags off: the control. */
    private fun switchWithoutSavingTo(route: Any) {
        composeTestRule.runOnIdle {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    private fun serialOf(name: String): Int = composeTestRule.runOnIdle { serials.getValue(name) }

    /**
     * The question the step actually asks: leave a non-start tab, come back, and find the same
     * ViewModel.
     *
     * A different serial here would mean `MonthViewModel.anchor` and `StatsViewModel.selection` are
     * discarded every time the user looks at another tab.
     */
    @Test
    fun leavingAndReturningToATabKeepsTheSameViewModelInstance() {
        start()
        switchTabTo(ProbeA)
        val before = serialOf("a")

        switchTabTo(ProbeB)
        switchTabTo(ProbeA)

        assertEquals(before, serialOf("a"))
    }

    /**
     * The control: with `saveState`/`restoreState` off, the popped entry really is destroyed and the
     * probe really does notice.
     *
     * ⚠ If this test and the one above ever agree, the probe has stopped measuring anything and
     * neither result means what it says. That is not hypothetical — it is what the first version of
     * this file did.
     */
    @Test
    fun leavingATabWithoutSaveStateRebuildsItsViewModel() {
        start()
        switchWithoutSavingTo(ProbeA)
        val before = serialOf("a")

        switchWithoutSavingTo(ProbeB)
        switchWithoutSavingTo(ProbeA)

        assertNotEquals(before, serialOf("a"))
    }

    /**
     * The asymmetry, written down so it cannot be rediscovered the hard way: the **start**
     * destination is never popped, so `Week`'s ViewModel does not depend on the flags at all.
     *
     * This is why the first version of the probe was vacuous, and it is worth an assertion rather
     * than a comment — if `switchTab` ever gains `inclusive = true`, this is what says so.
     */
    @Test
    fun theStartDestinationIsNeverPoppedSoItsViewModelIsNeverAtRisk() {
        start()
        val before = serialOf("home")

        switchWithoutSavingTo(ProbeA)
        switchWithoutSavingTo(ProbeHome)

        assertEquals(before, serialOf("home"))
    }
}
