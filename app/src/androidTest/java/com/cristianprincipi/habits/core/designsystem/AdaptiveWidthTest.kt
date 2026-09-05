package com.cristianprincipi.habits.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.feature.history.MonthContent
import com.cristianprincipi.habits.feature.history.MonthUiState
import com.cristianprincipi.habits.feature.settings.SettingsContent
import com.cristianprincipi.habits.feature.settings.SettingsUiState
import com.cristianprincipi.habits.feature.week.WeekContent
import com.cristianprincipi.habits.feature.week.WeekUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/**
 * M12.8 — the app uses the width it is given, and **stops doing so before it reaches a phone**.
 *
 * ### What was wrong, measured before any of this was written
 *
 * On a Pixel 9 rotated to landscape (**923 × 411dp**) every screen was a single column stretched to
 * the whole window: Week showed **one card, clipped**, with its pills drawn at ~91dp against a
 * design that draws 44dp; Month showed two, with dot cells ballooned to ~120dp against a designed
 * ~13dp; a Settings row was a 923dp-wide button with one word in the middle.
 *
 * ### ⚠⚠ The assertion that matters most is the one about phones
 *
 * Everything here could be "fixed" by a change that also reshaped the design on the device the
 * design was drawn for, and no other test in this repo would notice — every render test composes
 * into whatever size the host gives it and asserts about *content*, never about columns. So each
 * pair below is **the wide case and its phone control**, and the control is the half that fails if
 * a threshold is set carelessly.
 *
 * [monthKeepsTwoColumnsOnTheNarrowestPhone] is the sharpest of them: ADR-091 §5 sketched
 * `Adaptive(~160.dp)` as the cheap fix, and 160 drops Month to a **single** column on a 360dp phone
 * — changing the design on the narrowest device it has to be right on, while every wide-window
 * assertion here still passes.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveWidthTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /** The reference device in portrait — `docs/decisions.md` puts the Pixel 9 at 411dp. */
    private val phonePortrait = DpSize(411.dp, 800.dp)

    /** The same device rotated. Both carried findings were measured here. */
    private val phoneLandscape = DpSize(923.dp, 411.dp)

    /** The narrowest phone the app supports — the control for every column threshold. */
    private val narrowPhone = DpSize(360.dp, 800.dp)

    /** A tablet in portrait, where nothing was broken but everything was loose. */
    private val tablet = DpSize(800.dp, 1280.dp)

    private data class DpSize(
        val width: Dp,
        val height: Dp,
    )

    private fun showIn(
        size: DpSize,
        content: @Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            HabitsTheme {
                // ⚠ **`requiredSize`, not `size`** — and the difference is the whole test.
                // `Modifier.size` is coerced into the parent's constraints, so a "923dp" box inside
                // a 411dp test host is silently 411dp and every landscape assertion here measured
                // a portrait window while reading as if it had not. `requiredSize` ignores the
                // incoming constraints, which is the only way to put a phone-shaped host in
                // landscape.
                Box(modifier = Modifier.requiredSize(size.width, size.height).testTag(WINDOW)) { content() }
            }
        }
    }

    private fun showWeek(size: DpSize) =
        showIn(size) {
            WeekContent(
                state =
                    WeekUiState.Content(
                        weekStart = SampleHabits.referenceDate,
                        habits = SampleHabits.weekPeriods(SampleHabits.referenceDate),
                        isCurrentWeek = true,
                        currentYear = SampleHabits.referenceDate.year,
                    ),
                onEvent = {},
                onOpenSyncStatus = {},
            )
        }

    private fun showMonth(size: DpSize) =
        showIn(size) {
            val month = YearMonth.from(SampleHabits.referenceDate)
            MonthContent(
                state =
                    MonthUiState.Content(
                        month = month,
                        habits = SampleHabits.monthPeriods(SampleHabits.referenceDate, monthOf = month),
                        isCurrentMonth = true,
                    ),
                onEvent = {},
                onOpenSyncStatus = {},
            )
        }

    private fun showSettings(size: DpSize) =
        showIn(size) {
            SettingsContent(
                state = SettingsUiState(connection = ConnectionSummary(isConnected = false, lastSynced = null)),
                onOpenSyncStatus = {},
                onOpenHabits = {},
                onOpenAbout = {},
            )
        }

    /** The first two habits' cards, addressed by the names on them. */
    private fun firstCard() = composeTestRule.onNodeWithText(SampleHabits.habits()[0].name)

    private fun secondCard() = composeTestRule.onNodeWithText(SampleHabits.habits()[1].name)

    private fun assertStacked(message: String) {
        val first = firstCard().getUnclippedBoundsInRoot()
        val second = secondCard().getUnclippedBoundsInRoot()

        assertSameDp("$message — cards are not in the same column", first.left, second.left)
        assertTrue("$message — cards share a row", second.top > first.top)
    }

    private fun assertSideBySide(message: String) {
        val first = firstCard().getUnclippedBoundsInRoot()
        val second = secondCard().getUnclippedBoundsInRoot()

        assertSameDp("$message — cards are not on the same row", first.top, second.top)
        assertTrue("$message — cards are not in different columns", second.left > first.left)
    }

    /**
     * ⚠ Dp equality has to carry a tolerance: these are pixels divided by a density of 2.625, so two
     * positions that are the same to the layout differ in the fourth decimal place.
     */
    private fun assertSameDp(
        message: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(message, expected.value, actual.value, TOLERANCE)

    // --- The three habit-card tabs ---------------------------------------------------------

    @Test
    fun aPhoneInPortraitGetsTheSingleColumnTheDesignDraws() {
        showWeek(phonePortrait)

        assertStacked("Week on a 411dp phone")
    }

    @Test
    fun andSoDoesTheNarrowestPhone() {
        showWeek(narrowPhone)

        assertStacked("Week on a 360dp phone")
    }

    @Test
    fun landscapeGetsASecondColumnInsteadOfOneStretchedCard() {
        // The M6.11 finding, inverted into an assertion: this window used to hold one clipped card.
        showWeek(phoneLandscape)

        assertSideBySide("Week in landscape")
    }

    @Test
    fun andSoDoesATablet() {
        showWeek(tablet)

        assertSideBySide("Week on an 800dp tablet")
    }

    /**
     * ⭐⭐ The control that catches a carelessly chosen threshold, and the reason it is 155 not 160.
     *
     * The design draws Month two-up on every phone. At `Adaptive(160.dp)` — ADR-091 §5's own sketch
     * — a 360dp phone falls to one column, and every other assertion in this file still passes.
     */
    @Test
    fun monthKeepsTwoColumnsOnTheNarrowestPhone() {
        showMonth(narrowPhone)

        assertSideBySide("Month on a 360dp phone")
    }

    @Test
    fun andMonthKeepsThemOnTheReferencePhoneToo() {
        showMonth(phonePortrait)

        assertSideBySide("Month on a 411dp phone")
    }

    /**
     * Month's landscape failure was **ballooning, not sparseness** — the dot cell is `cardWidth / 7`
     * and square, so a wider card drew a bigger day. More columns is what makes each day smaller.
     */
    @Test
    fun landscapeShrinksMonthsDaysByGivingItMoreColumns() {
        // ⚠ One render per test. `setContent` may be called once per rule, so "measure portrait,
        // then measure landscape" is not available — the comparison is against the card's own
        // designed width instead, which is the number that actually matters.
        showMonth(phoneLandscape)

        val first = firstCard().getUnclippedBoundsInRoot()
        val third = composeTestRule.onNodeWithText(SampleHabits.habits()[2].name).getUnclippedBoundsInRoot()

        // Three cards on one row means at least three columns, which is what shrinks each day.
        assertSameDp("Month in landscape does not fit three cards on a row", first.top, third.top)
        assertTrue(
            "a landscape Month card is ${first.width}, no narrower than the two-up phone card",
            first.width < phonePortrait.width / 2,
        )
    }

    // --- The reading screens ----------------------------------------------------------------

    /**
     * A Settings row on a phone is as wide as the window allows — the cap must not bite here.
     *
     * This is the whole claim of `readingWidth`: inert below about 472dp, so the screens the design
     * specifies are the screens the design specifies.
     */
    @Test
    fun aReadingColumnIsUncappedOnAPhone() {
        showSettings(phonePortrait)

        val row = composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).getUnclippedBoundsInRoot()

        assertSameDp("the cap bit on a phone", phonePortrait.width - Spacing.screenPadding * 2, row.width)
    }

    @Test
    fun andIsCappedOnAWideOne() {
        showSettings(phoneLandscape)

        val row = composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).getUnclippedBoundsInRoot()

        // It used to be the window: a 923dp button with one word floating in the middle of it.
        assertTrue(
            "a Settings row is ${row.width} wide on a ${phoneLandscape.width} window",
            row.width <= Sizing.readingMaxWidth,
        )
        assertTrue("the row was not capped at all", row.width < phoneLandscape.width - Spacing.screenPadding * 2)
    }

    /**
     * And it is centred rather than pinned left, which is what makes the cap read as a choice.
     *
     * ⚠ **The gaps are measured against the simulated window, not against the root**, and getting
     * that wrong is what made this test fail first time round. A `requiredSize` box larger than the
     * host is *centred* in it, so the box starts at a negative root x — a column correctly centred
     * inside a 923dp box reported a 1.9dp left gap against a 513dp right one, and read exactly like
     * content pinned to the left edge.
     */
    @Test
    fun andTheCappedColumnIsCentred() {
        showSettings(phoneLandscape)

        val window = composeTestRule.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        val row = composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).getUnclippedBoundsInRoot()

        assertSameDp("the column is not centred", row.left - window.left, window.right - row.right)
    }

    private companion object {
        /** A pixel's worth of rounding, in dp at this density. */
        const val TOLERANCE = 1.0f

        const val WINDOW = "adaptive-window"
    }
}
