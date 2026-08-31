package com.cristianprincipi.habits.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.MonthHabitCard
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.HabitsEmpty
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.core.ui.rememberCurrentLocale
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.core.ui.toDayStatus
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.PeriodCell

/**
 * The Month view, stateless — `docs/design-spec.md` §2.
 *
 * Two columns of habit cards, each one a month of days at a glance. **Read-only throughout**: no
 * cell here has a click handler, and `MonthDotGrid` clears its own semantics so a screen reader is
 * not read 31 undifferentiated squares (ADR-009, `CLAUDE.md` §1).
 */
@Composable
fun MonthContent(
    state: MonthUiState,
    onEvent: (MonthEvent) -> Unit,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = rememberCurrentLocale()

    Column(modifier = modifier.fillMaxSize()) {
        MonthHeader(state = state, onEvent = onEvent, locale = locale)

        when (state) {
            is MonthUiState.Loading -> {
                // Nothing under the header. A spinner for a Room query that returns in a frame is a
                // flash of chrome, and `CLAUDE.md` §1 forbids the UI awaiting anything slower.
                Unit
            }

            is MonthUiState.Empty -> {
                HabitsEmpty(
                    reason = state.reason,
                    onOpenSyncStatus = onOpenSyncStatus,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is MonthUiState.Content -> {
                MonthGrid(habits = state.habits, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * The two-column grid the design specifies, at 16dp padding and a 12dp gap both ways.
 *
 * `LazyVerticalGrid` rather than a `Column` of `Row`s: the list is unbounded in principle — a Notion
 * database can carry any number of checkbox columns — and `CLAUDE.md` §6 rules out rendering one of
 * those eagerly. Keyed on the habit id so a re-sync that reorders the set moves cards rather than
 * rebuilding them.
 */
@Composable
private fun MonthGrid(
    habits: List<HabitPeriod>,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(MONTH_GRID_COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        items(items = habits, key = { period -> period.habit.id.value }) { period ->
            MonthHabitRow(period = period)
        }
    }
}

@Composable
private fun MonthHabitRow(period: HabitPeriod) {
    MonthHabitCard(
        name = period.habit.name,
        description = period.summary(),
        emoji = period.habit.emoji,
        habitColor = period.habit.color.resolve(),
        days = period.cells.map { cell -> cell.toDayStatus() },
    )
}

/**
 * The card's second line — ADR-027's empty slot, filled the way M6 filled Week's.
 *
 * ⭐⭐ **The denominator is days the habit could have been kept, not days in the month** — the
 * user's call at M8.7 (ADR-091). Paging back to before a habit existed printed `0 of 28`, which is
 * literally true and reads as twenty-eight missed days for a habit that did not exist yet.
 *
 * ⚠ **And when there are none of those, the line says so rather than printing `0 of 0`.** That is
 * the case the whole change is for, so it must not be the case that falls through to arithmetic.
 */
@Composable
private fun HabitPeriod.summary(): String {
    val eligible = eligibleCount
    return if (eligible == 0) {
        stringResource(R.string.habit_card_not_started)
    } else {
        stringResource(R.string.habit_card_summary, completedCount, eligible)
    }
}

private val HabitPeriod.completedCount: Int
    get() = cells.count { cell -> cell is PeriodCell.Day && cell.state == DayState.COMPLETED }

/**
 * Days of the month on or after the habit started — **not** cells in the grid, and not days either.
 *
 * Three counts are wrong here and each is wrong differently. `cells.size` includes the up-to-six
 * `Padding` cells that push the 1st onto its real weekday, so August would read `21 of 36`. Counting
 * every `Day` gives the calendar month, which is what printed `0 of 28` for a habit that had not
 * started. This counts the days the habit was actually answerable for.
 *
 * `BEFORE_START` is exactly that distinction, already made: `buildPeriod` assigns it to any day
 * before `habit.startDate`. ⚠ **A completion outranks it** — a check-off recorded before the start
 * date is `COMPLETED` and counts — which is the same precedence `HabitPeriods` documents, and the
 * honest one: a day the user ticked is a day that happened.
 *
 * ⚠ Future days **do** count, unlike the Year card's rate, which stops at today. That keeps this
 * line reading like Week's approved `6 of 7`; the three screens and their three denominators are
 * laid out in `strings.xml` beside `habit_card_summary`.
 */
private val HabitPeriod.eligibleCount: Int
    get() = cells.count { cell -> cell is PeriodCell.Day && cell.state != DayState.BEFORE_START }

private const val MONTH_GRID_COLUMNS = 2

@Preview(name = "Month", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun MonthContentPreview() {
    HabitsTheme {
        MonthContent(
            state =
                MonthUiState.Content(
                    month = SampleHabits.referenceMonth,
                    habits = SampleHabits.monthPeriods(),
                    isCurrentMonth = true,
                ),
            onEvent = {},
            onOpenSyncStatus = {},
        )
    }
}

@Preview(
    name = "Month — empty, first import on its way",
    backgroundColor = 0xFF111113,
    showBackground = true,
    widthDp = 360,
    heightDp = 400,
)
@Composable
private fun MonthContentEmptyPreview() {
    HabitsTheme {
        MonthContent(
            state =
                MonthUiState.Empty(
                    month = SampleHabits.referenceMonth,
                    reason = HabitsEmptyReason.SyncPending,
                ),
            onEvent = {},
            onOpenSyncStatus = {},
        )
    }
}
