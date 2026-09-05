package com.cristianprincipi.habits.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.YearHabitCard
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.HabitsEmpty
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.core.ui.toDayStatus
import com.cristianprincipi.habits.domain.usecase.HabitYear

/**
 * The Year view, stateless — `docs/design-spec.md` §4.
 *
 * One card per habit: a year of days as a density grid, with the headline completion figure beside
 * the name. **Read-only throughout** — a cell here is 4dp, which is a quarter of an unusable touch
 * target, so check-off lives on the Week pills and nowhere else (ADR-009, `CLAUDE.md` §1).
 *
 * Takes no `onEvent`, unlike [MonthContent]. There is nothing to send: the design gives this view no
 * sub-navigation, and the window is always the trailing 365 days ending today.
 */
@Composable
fun YearContent(
    state: YearUiState,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        YearTitle()

        when (state) {
            YearUiState.Loading -> {
                // Nothing under the header. A spinner for a Room query that returns in a frame is a
                // flash of chrome, and `CLAUDE.md` §1 forbids the UI awaiting anything slower.
                Unit
            }

            is YearUiState.Empty -> {
                HabitsEmpty(
                    reason = state.reason,
                    onOpenSyncStatus = onOpenSyncStatus,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is YearUiState.Content -> {
                YearList(habits = state.habits, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * The header, which is one word.
 *
 * Inline rather than a `YearHeader.kt` beside `MonthHeader.kt`, because there is nothing to put in
 * one: no sub-nav, no jump-to-today, and no `+` — the design draws that last one, it is inert even
 * in the prototype, and ADR-010 means it must never gain a handler. A file whose whole content is a
 * `Text` would imply this screen has chrome it does not have.
 */
@Composable
private fun YearTitle() {
    Text(
        text = stringResource(R.string.screen_title_year),
        style = MaterialTheme.typography.headlineSmall,
        color = HabitsTheme.colors.text.primary,
        // ⚠ Horizontal padding only, and NO bottom padding — matching `WeekHeader` and
        // `MonthHeader`, whose `Arrangement.spacedBy(headerBottom)` puts that 4dp *between* a title
        // and a sub-nav and adds nothing when there is no sub-nav to separate from. Adding it here
        // started this list 20dp below its title where the other two tabs start theirs at 16dp,
        // which is only visible by switching tabs on a device — seen in M8.7's shell walk.
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.headerHorizontal),
    )
}

@Composable
private fun YearList(
    habits: List<HabitYear>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        // ⭐ M12.5 — see `Motion`'s closing note for why this is a platform spring and not a token.
        items(items = habits, key = { year -> year.period.habit.id.value }) { year ->
            YearHabitRow(year = year, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun YearHabitRow(
    year: HabitYear,
    modifier: Modifier = Modifier,
) {
    val habit = year.period.habit

    YearHabitCard(
        modifier = modifier,
        name = habit.name,
        // ⭐ The two numbers BEHIND the percentage beside it, not a second measurement.
        //
        // ADR-027 emptied this slot and M6 filled it with a count; the Year card is the one that
        // also carries a figure, so the count is chosen to agree with it. `eligibleDays` — days on
        // or after the habit's start — is the percentage's own denominator, so a habit that started
        // three months ago reads `66 of 95` beside `69%` and answers "69% of what?". Counting the
        // whole 365 instead would put two numbers on one card that cannot both be the rate.
        description =
            stringResource(
                R.string.habit_card_summary,
                year.rate.completedDays,
                year.rate.eligibleDays,
            ),
        emoji = habit.emoji,
        habitColor = habit.color.resolve(),
        completionLabel = stringResource(R.string.percent_value, year.rate.percent),
        completionEyebrow = stringResource(R.string.year_completion_eyebrow),
        days = year.period.cells.map { cell -> cell.toDayStatus() },
    )
}

@Preview(name = "Year", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun YearContentPreview() {
    HabitsTheme {
        YearContent(state = YearUiState.Content(SampleHabits.years()), onOpenSyncStatus = {})
    }
}

@Preview(
    name = "Year — empty, first import on its way",
    backgroundColor = 0xFF111113,
    showBackground = true,
    widthDp = 360,
    heightDp = 400,
)
@Composable
private fun YearContentEmptyPreview() {
    HabitsTheme {
        YearContent(
            state = YearUiState.Empty(HabitsEmptyReason.SyncPending),
            onOpenSyncStatus = {},
        )
    }
}
