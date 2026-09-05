package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M12.5 — the check-off **crosses** rather than cuts, and the proof has to be pixels.
 *
 * ### Why this test exists at all
 *
 * `App.tsx:222` puts `transition-all` on the prototype's day pill, so its fill moves over 150ms.
 * Ours snapped until this step. Nothing else in the suite can tell the difference: the semantics
 * tree carries `ToggleableState.On` the instant the state flips, whether the fill took 150ms to get
 * there or no time at all. **Delete the `animateColorAsState` and every other test still passes.**
 *
 * ### The clock is stopped, and the fill is read off the screen
 *
 * `mainClock.autoAdvance = false` freezes the frame clock, so the pill can be caught mid-crossing
 * and the pixel under it sampled. Three readings make the claim:
 *
 * 1. **At rest, incomplete** — the tint, whatever the theme composites it to.
 * 2. **Half way** — must differ from *both* ends. This is the assertion that fails without the
 *    animation, because a snapped pill is already at its destination on the first frame.
 * 3. **After the duration** — must equal the habit colour exactly, which a completed pill is filled
 *    with opaquely. So the animation is also shown to *finish*, and to finish where it should: a
 *    spring that overshoots, or a duration that outlives the gesture, fails here.
 *
 * ⚠ **The sample is taken off-centre on purpose.** The tick appears at the pill's middle the moment
 * the day completes — deliberately un-animated, because CSS cannot transition an inserted element
 * either — so the centre pixel is white long before the fill has arrived.
 */
@RunWith(AndroidJUnit4::class)
class WeekPillMotionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val habitColor = Color(0xFF4ADE80)

    private var completed by mutableStateOf(false)

    private fun showPills() {
        composeTestRule.setContent {
            HabitsTheme {
                // An opaque ground, so a tint's alpha composites against a known colour rather than
                // whatever the test host happens to draw behind it.
                Box(
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .width(PILL_ROW_WIDTH.dp),
                ) {
                    OneDay(completed = completed)
                }
            }
        }
    }

    @Composable
    private fun OneDay(completed: Boolean) {
        WeekPills(
            days = List(A_WEEK) { if (completed) DayStatus.COMPLETED else DayStatus.INCOMPLETE },
            dayLabels = PREVIEW_DAY_LABELS,
            dayContentDescriptions = List(A_WEEK) { index -> "day $index" },
            habitColor = habitColor,
            todayIndex = -1,
            onToggle = {},
        )
    }

    /**
     * The colour of the first pill, sampled clear of the tick that lands in its middle.
     *
     * The captured node is the 48dp touch target, which is the full width of the pill and taller
     * than the 36dp drawn inside it — so an eighth of the way across and half way down is inside the
     * fill and outside the glyph.
     */
    private fun fillColor(): Color {
        val pixels = composeTestRule.onNodeWithContentDescription("day 0").captureToImage().toPixelMap()
        return pixels[pixels.width / GLYPH_CLEARANCE, pixels.height / 2]
    }

    @Test
    fun theFillCrossesInsteadOfCutting() {
        showPills()
        val atRest = fillColor()

        composeTestRule.mainClock.autoAdvance = false
        completed = true
        composeTestRule.mainClock.advanceTimeBy(Motion.STATE_CHANGE_MILLIS.toLong() / 2)

        val halfWay = fillColor()

        assertNotEquals("the pill has not started moving", atRest, halfWay)
        assertNotEquals("the pill arrived at once — the fill is cutting, not crossing", habitColor, halfWay)
    }

    @Test
    fun andItArrivesOnTheHabitColourWhenTheDurationIsUp() {
        showPills()

        composeTestRule.mainClock.autoAdvance = false
        completed = true
        composeTestRule.mainClock.advanceTimeBy(Motion.STATE_CHANGE_MILLIS.toLong() + SETTLE_MARGIN)

        // A completed pill is filled opaquely, so the composited pixel is the habit colour itself.
        assertEquals(habitColor, fillColor())
    }

    /**
     * ⚠ The control, and the reason the test above is not vacuous.
     *
     * Half of `theFillCrossesInsteadOfCutting` is an inequality, and an inequality passes for the
     * wrong reason if the pill never reaches its destination at all. Taking the *same* reading with
     * the clock left running proves the two states are genuinely different colours — so the mid-
     * flight sample being unlike both of them means something.
     */
    @Test
    fun theTwoRestingStatesAreDifferentColoursToBeginWith() {
        showPills()
        val incomplete = fillColor()

        completed = true
        composeTestRule.waitForIdle()

        assertNotEquals(incomplete, fillColor())
        assertEquals(habitColor, fillColor())
    }

    private companion object {
        const val A_WEEK = 7
        const val PILL_ROW_WIDTH = 360
        const val GLYPH_CLEARANCE = 8
        const val SETTLE_MARGIN = 32L
    }
}
