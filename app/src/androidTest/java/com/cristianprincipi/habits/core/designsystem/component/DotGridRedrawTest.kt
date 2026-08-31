package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * M8.6 — what a state change costs the two dot grids.
 *
 * ### ⚠ The step's own wording describes something that cannot happen
 *
 * `Plan.md` M8.6 asks that "neither grid may recompose per-cell on state change", verified with a
 * recomposition count. **Neither grid has per-cell composables to count.** Both are a single
 * `Canvas` — that is the decision the step exists to protect, and M2 already took it (ADR-086 §1).
 * A literal per-cell count would measure a thing the code does not contain and pass for ever, which
 * is the failure mode ADR-088 §3 caught in M8.4's own scroll assertion.
 *
 * ### So this measures the question underneath: does an unrelated change redraw the grid?
 *
 * A redraw of a Year grid is 371 `drawRoundRect` calls. Doing that because a *sibling* changed is
 * the real version of the waste the step was worried about, and it is measurable from outside
 * through the public `modifier` parameter.
 *
 * ### ⚠ What was measured, and it is not what the step hoped
 *
 * **Both grids redraw in full when a sibling changes**: `before=1, after=2`, which for a Year grid
 * is 371 `drawRoundRect` calls spent because a `Text` beside it changed. The cause is stability —
 * `days: List<DayStatus>` is an interface, so the Compose compiler treats it as unstable and
 * neither component is skippable.
 *
 * ⭐ **Remembering the counting modifier changes nothing, and that is the evidence.** A probe built
 * inline is a new lambda on every recomposition and invalidates the draw node by itself, so a *skippable*
 * grid would read 1 remembered and 2 inline. Both read 2. The probe is `remember`ed anyway, so that
 * this test keeps measuring the component rather than itself once that is no longer true.
 *
 * ### So the assertion is a bound, not an equality
 *
 * `at most one redraw per state change` passes today, keeps passing if the grids are later made
 * skippable (ADR-090's deferred note), and fails if a change makes a single state write cost two
 * draw passes — a layout that thrashes, or an effect that writes state the draw reads. Asserting
 * *exactly* one would be a change detector pointed at an improvement.
 */
@RunWith(AndroidJUnit4::class)
class DotGridRedrawTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val monthDays = List(FIVE_BLANKS) { DayStatus.PADDING } + List(AUGUST) { DayStatus.COMPLETED }
    private val yearDays = List(A_YEAR) { DayStatus.COMPLETED }

    /**
     * Drives a recomposition of the grid's **sibling**, not of the grid.
     *
     * The grid's own arguments are held constant across it: the same list instance, the same colour.
     * Anything the grid then does is work it chose, not work it was given.
     */
    @Composable
    private fun ScreenAround(
        draws: AtomicInteger,
        ticks: Int,
        grid: @Composable (Modifier) -> Unit,
    ) {
        val probe =
            remember {
                Modifier.drawWithContent {
                    draws.incrementAndGet()
                    drawContent()
                }
            }

        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                // The unrelated part. Reading `ticks` here is what makes this lambda recompose.
                Text(text = "$ticks")
                grid(Modifier.width(WIDTH).then(probe))
            }
        }
    }

    private fun redrawsFromASiblingChange(grid: @Composable (Modifier) -> Unit): Pair<Int, Int> {
        val draws = AtomicInteger()
        var ticks by mutableIntStateOf(0)

        composeTestRule.setContent {
            HabitsTheme { ScreenAround(draws = draws, ticks = ticks, grid = grid) }
        }
        composeTestRule.waitForIdle()

        val before = draws.get()
        ticks++
        composeTestRule.waitForIdle()

        return before to draws.get()
    }

    @Test
    fun oneStateChangeCostsTheMonthGridAtMostOneRedraw() {
        val (before, after) =
            redrawsFromASiblingChange { modifier ->
                MonthDotGrid(
                    days = monthDays,
                    habitColor = HabitsTheme.colors.habit.blue,
                    modifier = modifier,
                )
            }

        assertTrue(
            "one state change should cost at most one redraw, went from $before to $after",
            after <= before + 1,
        )
    }

    @Test
    fun oneStateChangeCostsTheYearGridAtMostOneRedraw() {
        // The one that matters if it ever multiplies: a redraw here is 371 `drawRoundRect` calls,
        // and three screens draw this component (`docs/design-spec.md` §4, §5.2).
        val (before, after) =
            redrawsFromASiblingChange { modifier ->
                YearDotGrid(
                    days = yearDays,
                    habitColor = HabitsTheme.colors.habit.blue,
                    modifier = modifier,
                    dotSize = Sizing.yearDotSmall,
                )
            }

        assertTrue(
            "one state change should cost at most one redraw, went from $before to $after",
            after <= before + 1,
        )
    }

    private companion object {
        val WIDTH = 300.dp
        const val FIVE_BLANKS = 5
        const val AUGUST = 31
        const val A_YEAR = 371
    }
}
