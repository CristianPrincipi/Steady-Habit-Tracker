package com.cristianprincipi.habits.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.HabitPeriod
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * M13.5 — **does `compose-stability.conf` actually do anything?**
 *
 * ### ⚠⚠ Why this test has to exist
 *
 * The stability configuration file is a **promise the compiler takes on faith**. It checks nothing,
 * and it fails **silently**: a typo in a fully-qualified name, a renamed class, a file the build no
 * longer points at — none of those is an error, and all of them quietly restore the cost the file
 * was added to remove. The compiler report shows the classes flipped `unstable → stable`, but a
 * report is a build artefact nobody regenerates; this runs on every commit.
 *
 * ### What it measures, and why it can measure it at all
 *
 * Skipping is invisible from the outside — except through a composable's own body not running. So
 * the probe below is a composable that counts its own executions, and the test recomposes its parent
 * while handing it a value that is **equal but not the same instance** — precisely what
 * `GetHabitsForWeek` hands the Week screen on every check-off (`RecompositionCostTest`).
 *
 * - Stable parameter → compared with `equals` → the body does not run again.
 * - Unstable parameter → strong skipping compares by **instance** → the body runs again.
 *
 * ⭐ **The two tests below are the same experiment on two types**, and the second is what makes the
 * first mean anything: an unlisted type must still recompose. Without it, a probe that simply never
 * re-ran would report success for a configuration file that was never read.
 */
@RunWith(AndroidJUnit4::class)
class StabilityConfigTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * A type deliberately **absent** from `compose-stability.conf`, shaped like the ones in it.
     *
     * ⚠ Its `List` is what makes it unstable — `List` is an interface, so Compose cannot know an
     * implementation is not mutable. That is the same single reason `HabitPeriod` was unstable, which
     * is what makes this a fair control rather than a straw man.
     */
    private data class Unlisted(
        val name: String,
        val values: List<Int>,
    )

    /**
     * Counts every composition that is not skipped. The whole measurement is that number.
     *
     * ⚠⚠ **The counter is an `AtomicInteger` passed in, not a captured `var`, and that is not
     * fussiness — it is the second bug this test had.** Kotlin compiles a captured local `var` into
     * a `Ref.IntRef`, which Compose treats as **unstable**, so the lambda closing over it could not
     * be memoised and arrived as a new instance on every pass. The probe then recomposed because of
     * its *lambda* while the test believed it was measuring the *period*, and reported the exact
     * failure a broken configuration file would.
     */
    @Composable
    private fun StableProbe(
        period: HabitPeriod,
        compositions: AtomicInteger,
    ) {
        // ⚠⚠ The parameter is READ, and that is load-bearing — see [UnlistedProbe].
        compositions.addAndGet(period.cells.size.coerceAtMost(1))
    }

    /**
     * ⚠⚠ **The parameter has to be used, and this is the third vacuous check this milestone.**
     *
     * The first draft of both probes ignored the typed parameter and only bumped the counter. The
     * compiler report said it plainly — `unused unstable value: Unlisted` — and an **unused**
     * parameter cannot affect a composable's output, so Compose leaves it out of the skip
     * comparison entirely. Both probes then skipped for a reason that had nothing to do with
     * stability, and the "stable" one *passed*.
     *
     * Reading the parameter is what puts it back into the comparison. ADR-108 §2, ADR-110 §6 and
     * ADR-111 §4 are the same lesson; this is the one that caught it here.
     */
    @Composable
    private fun UnlistedProbe(
        value: Unlisted,
        compositions: AtomicInteger,
    ) {
        compositions.addAndGet(value.values.size.coerceAtMost(1))
    }

    /**
     * ⭐⭐ **The assertion the whole step turns on.**
     *
     * A fresh, equal `HabitPeriod` must not re-run the child. If this fails, the configuration file
     * has stopped being read — and the Week screen is silently recomposing every card on every tap
     * again, which nothing else in the suite would notice.
     */
    @Test
    fun anEqualButFreshlyBuiltHabitPeriodDoesNotRecomposeItsCard() {
        var trigger by mutableStateOf(0)
        // ⚠ Not snapshot state. Writing a `MutableState` from a composable body schedules another
        // pass to settle it, and a body that writes on every pass never settles — the first draft
        // deadlocked the rule with "compose is busy" rather than failing.
        val compositions = AtomicInteger()

        composeTestRule.setContent {
            // Read the trigger *here*, so the parent recomposes and the child is offered a new
            // instance — the shape of a state emission, rather than of a changed value.
            @Suppress("UNUSED_EXPRESSION")
            trigger
            StableProbe(period = freshPeriod(), compositions = compositions)
        }

        composeTestRule.waitForIdle()
        assertEquals("the probe did not compose at all", 1, compositions.get())

        composeTestRule.runOnIdle { trigger++ }
        composeTestRule.waitForIdle()

        assertEquals(
            "an equal HabitPeriod recomposed its card — compose-stability.conf is not being applied",
            1,
            compositions.get(),
        )
    }

    /**
     * ⚠⚠ **The control, and without it the test above is unfalsifiable.**
     *
     * ADR-108 §2, ADR-110 §6 and ADR-111 §4 each caught a check that could not fail. Here the failure
     * mode would be a probe that never re-runs for a reason of its own — a parent that does not
     * actually recompose, a value hoisted out of the loop — and this rules it out by demanding the
     * *opposite* result from the same machinery on a type the config does not list.
     */
    @Test
    fun andAnUnlistedTypeStillRecomposes() {
        var trigger by mutableStateOf(0)
        val compositions = AtomicInteger()

        composeTestRule.setContent {
            @Suppress("UNUSED_EXPRESSION")
            trigger
            UnlistedProbe(value = Unlisted("read", listOf(1, 2, 3)), compositions = compositions)
        }

        composeTestRule.waitForIdle()
        assertEquals(1, compositions.get())

        composeTestRule.runOnIdle { trigger++ }
        composeTestRule.waitForIdle()

        assertEquals(
            "an unlisted, List-holding type skipped — the probe cannot tell stable from unstable",
            2,
            compositions.get(),
        )
    }

    /** A new instance every call, equal to the last — what a Room emission produces. */
    private fun freshPeriod(): HabitPeriod = SampleHabits.weekPeriods(SampleHabits.referenceDate).first()
}
