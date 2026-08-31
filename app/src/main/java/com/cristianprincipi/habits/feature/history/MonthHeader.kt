package com.cristianprincipi.habits.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.monthLabel
import com.cristianprincipi.habits.core.datetime.monthName
import com.cristianprincipi.habits.core.designsystem.component.NavArrows
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import java.time.YearMonth
import java.util.Locale

/**
 * The Month view's title and its `‹ August 2026 ›` sub-nav — `docs/design-spec.md` §1, §2.
 *
 * ### ⚠ The month name appears twice, and that is the prototype's own doing
 *
 * `App.tsx` sets `headerTitles.month` to `MONTH_NAMES[refMonth]` and the sub-nav label to
 * `${MONTH_NAMES[refMonth]} ${refYear}`, so a paged-to August reads "August" over "August 2026".
 * Built that way because `CLAUDE.md` §1 gives the Figma design the final word on *what the app looks
 * like*, and the alternative — a constant "Month" title, matching every other tab — is a change to
 * the design rather than a translation of it. **Put to the user on a screenshot at M8.7.**
 *
 * ### No `+`, and no jump-to-today
 *
 * The design draws a `+` on this header. It has no `onClick` even in the prototype, and ADR-010
 * means it must never gain one: a habit is a checkbox **column** in the user's Notion database, so
 * creating one is a schema change this app is provably incapable of (ADR-082). It is not drawn.
 *
 * The sub-nav is hidden unless there are habits, for `WeekHeader`'s reason: with nothing on screen
 * to page through, an arrow that moves a label is a control that does nothing.
 */
@Composable
fun MonthHeader(
    state: MonthUiState,
    onEvent: (MonthEvent) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.headerHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.headerBottom),
    ) {
        Text(
            text = monthName(state.month, locale),
            style = MaterialTheme.typography.headlineSmall,
            color = HabitsTheme.colors.text.primary,
        )

        if (state is MonthUiState.Content) {
            NavArrows(
                label = monthLabel(state.month, locale),
                previousContentDescription = stringResource(R.string.month_previous),
                nextContentDescription = stringResource(R.string.month_next),
                onPrevious = { onEvent(MonthEvent.PreviousMonthPressed) },
                onNext = { onEvent(MonthEvent.NextMonthPressed) },
            )
        }
    }
}

private val previewMonth: YearMonth = YearMonth.from(SampleHabits.referenceDate)

@Preview(name = "Month header", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun MonthHeaderPreview() {
    HabitsTheme {
        MonthHeader(
            state = MonthUiState.Content(month = previewMonth, habits = emptyList()),
            onEvent = {},
            locale = Locale.UK,
        )
    }
}

@Preview(
    name = "Month header — paged to another year",
    backgroundColor = 0xFF111113,
    showBackground = true,
    widthDp = 360,
)
@Composable
private fun MonthHeaderPagedPreview() {
    HabitsTheme {
        MonthHeader(
            state = MonthUiState.Content(month = previewMonth.minusMonths(NINE_MONTHS), habits = emptyList()),
            onEvent = {},
            locale = Locale.UK,
        )
    }
}

@Preview(
    name = "Month header — no habits, no sub-nav",
    backgroundColor = 0xFF111113,
    showBackground = true,
    widthDp = 360,
)
@Composable
private fun MonthHeaderEmptyPreview() {
    HabitsTheme {
        MonthHeader(
            state = MonthUiState.Empty(month = previewMonth, reason = HabitsEmptyReason.SyncPending),
            onEvent = {},
            locale = Locale.UK,
        )
    }
}

private const val NINE_MONTHS = 9L
