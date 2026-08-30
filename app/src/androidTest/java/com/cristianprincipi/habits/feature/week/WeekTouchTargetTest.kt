package com.cristianprincipi.habits.feature.week

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * The size of the thing a finger has to hit — measured, rather than trusted.
 *
 * `WeekPills` has carried a 48dp target around its 36dp pill since M2, and every gate run since has
 * proved only that it compiles. The user asked for this one by name (`CLAUDE.md` §1), so M6.5 pins
 * it: **the target is bigger than the drawing, and a tap in the gap between them lands**.
 *
 * ### ⚠ 48dp is a height here. It is not a square, and on this screen it cannot be one
 *
 * The design draws seven pills across a card (`docs/design-spec.md` §3). On a 360dp-wide phone that
 * card gives its content about 296dp, so a pill's cell is roughly **37dp wide** — and seven 48dp
 * cells would need 336dp before a single gap. The only spare width in the row is the six
 * [Spacing.weekPillGap]s between the pills, and claiming those would still fall short on a small
 * phone while moving pixels the design does specify.
 *
 * So the measured width is **pinned and reported, not quietly redesigned** — see `Plan.md` M6.11.
 * What `CLAUDE.md` §1 actually asks for is the drawn 36dp *height* growing to 48dp, and that is a
 * claim this file can make honestly.
 *
 * **Measured on the Pixel 9 emulator (411dp wide): 44.2dp × 48dp per pill, with 6dp of dead gap
 * between each pair.** Claiming those gaps would make it 50.2dp there — a real 48dp square — and
 * still only 42.9dp on a 360dp phone, at the cost of moving pixels the design specifies. That is
 * the trade M6.11 puts to the user; this file exists so it is a decision rather than a discovery.
 *
 * The numbers below are literals rather than `Sizing` tokens on purpose: 48dp is Android's number
 * and 36dp is the design's, and a test that read our own tokens would keep passing if someone
 * lowered them.
 */
@RunWith(AndroidJUnit4::class)
class WeekTouchTargetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** Saturday 22 August 2026 — so the week runs Mon 17 – Sun 23 with Sunday still in the future. */
    private val reference = SampleHabits.referenceDate
    private val monday = startOfWeek(reference)
    private val subject: Habit = SampleHabits.habits().first()

    /**
     * One completion, on the Monday.
     *
     * `SampleHabits` generates its history from a keep-rate, so which days of this week are filled
     * is a property of a seeded generator. Handing it an explicit list instead makes **Tuesday**
     * reliably incomplete, which matters: an incomplete pill carries no check glyph, so a column of
     * pixels down its middle is the drawn pill and nothing else.
     */
    private val history = listOf(Completion(subject.id, monday))

    private val events = mutableListOf<WeekEvent>()

    /** The day every measurement below is taken on. */
    private val tuesday = monday.plusDays(1)

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun showWeek() {
        composeTestRule.setContent {
            HabitsTheme {
                WeekContent(
                    state =
                        WeekUiState.Content(
                            weekStart = monday,
                            habits = SampleHabits.weekPeriods(reference, history),
                            isCurrentWeek = true,
                            currentYear = reference.year,
                        ),
                    onEvent = { event -> events += event },
                    onOpenSyncStatus = {},
                )
            }
        }
    }

    /** One habit's pill for one date — the habit half is what keeps this from matching five nodes. */
    private fun pill(date: LocalDate): SemanticsNodeInteraction =
        composeTestRule.onNodeWithContentDescription(
            string(R.string.week_pill_description)
                .format(subject.name, accessibleDate(date, Locale.getDefault())),
            useUnmergedTree = true,
        )

    @Test
    fun aPillIsTargetedAtTheAndroidMinimumEvenThoughItDrawsLess() {
        showWeek()

        pill(tuesday).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aPillStillDrawsTheDesignsThirtySixDp() {
        // The other half of the claim. Growing the target is only correct if the drawing did not
        // grow with it — a 48dp pill would satisfy the guideline and be the wrong fix.
        showWeek()

        val pixels = pill(tuesday).captureToImage().toPixelMap()
        val column = pixels.width / 2
        // The target is 12dp taller than the pill, so its top row is whatever sits behind it.
        val behind = pixels[column, 0]
        val drawnRows = (0 until pixels.height).count { row -> pixels[column, row] != behind }
        val drawn = with(composeTestRule.density) { drawnRows.toDp() }

        assertEquals(36f, drawn.value, ANTIALIASING_TOLERANCE_DP)
    }

    @Test
    fun aTapAboveTheDrawnPillStillChecksTheDayOff() {
        // ⭐ The assertion this file exists for, and the only one that is about behaviour rather than
        // geometry: two pixels below the target's top edge is ~6dp above anything that is drawn, so
        // it lands where a bounds assertion alone could still be lying.
        showWeek()

        pill(tuesday).performTouchInput { click(Offset(width / 2f, 2f)) }

        assertEquals(tuesday, events.filterIsInstance<WeekEvent.DayToggled>().single().date)
    }

    @Test
    fun theSevenTargetsAreSeparatedByExactlyTheDesignsGap() {
        // What the row's dead space is, pinned so that claiming it later is a decision rather than a
        // side effect: six gaps of `weekPillGap` between seven equal cells. See the class KDoc.
        showWeek()

        val cells = (0 until DAYS).map { day -> pill(monday.plusDays(day.toLong())).getUnclippedBoundsInRoot() }

        cells.zipWithNext { left, right ->
            assertEquals(Spacing.weekPillGap.value, (right.left - left.right).value, LAYOUT_TOLERANCE_DP)
        }
        val widths = cells.map { cell -> (cell.right - cell.left).value }
        assertEquals("no day may be easier to hit than another", widths.min(), widths.max(), LAYOUT_TOLERANCE_DP)
    }

    private companion object {
        const val DAYS = 7

        /** The pill's top and bottom edges are straight lines, so blending costs a row or two. */
        const val ANTIALIASING_TOLERANCE_DP = 1.5f

        /** Weights divide a row in pixels; a cell can land half a dp either side of its neighbour. */
        const val LAYOUT_TOLERANCE_DP = 0.5f
    }
}
