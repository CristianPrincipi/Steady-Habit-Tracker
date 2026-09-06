package com.cristianprincipi.habits.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.monthLabel
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
 * ### ⚠ The title is the constant word, and the prototype's is not
 *
 * `App.tsx` sets `headerTitles.month` to `MONTH_NAMES[refMonth]` and the sub-nav label to
 * `${MONTH_NAMES[refMonth]} ${refYear}`, so a paged-to August reads "August" over "August 2026" —
 * the month name twice. M8.1 built exactly that, on the grounds that `CLAUDE.md` §1 gives the design
 * the final word; M8.7 put it on a device and **the user chose the constant word** (ADR-091).
 *
 * ⭐ **The empty state is what settled it.** With no habits — every first run, before the first sync
 * lands — the sub-nav is correctly hidden and the screen reads "August" over "Your habits are on
 * their way": a title with nothing on screen to refer to. The design has no empty state at all
 * (`docs/design-spec.md` §2 is content-only), so it offers no opinion here.
 *
 * ### No `+`, and a jump-to-today the design does not have
 *
 * The design draws a `+` on this header. It has no `onClick` even in the prototype, and ADR-010
 * means it must never gain one: a habit is a checkbox **column** in the user's Notion database, so
 * creating one is a schema change this app is provably incapable of (ADR-082). It is not drawn.
 *
 * ⭐ **`This month` is drawn, and the design has no such control** — the user's call at M8.7, on the
 * grounds that six taps back deserves one tap forward. It is disabled in place rather than hidden,
 * which is the rule chosen at M6.11 for Week's identical button.
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.screen_title_month),
                style = MaterialTheme.typography.headlineSmall,
                color = HabitsTheme.colors.text.primary,
            )
            if (state is MonthUiState.Content) {
                ThisMonthButton(
                    enabled = !state.isCurrentMonth,
                    onClick = { onEvent(MonthEvent.ThisMonthPressed) },
                )
            }
        }

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

/**
 * Back to the month containing today.
 *
 * Disabled in place rather than hidden, matching `WeekHeader`'s: a control that vanishes when it
 * cannot act is one the user has to rediscover, and the disabled state says "you are already there"
 * (ADR-060, one screen along).
 */
@Composable
private fun ThisMonthButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text = stringResource(R.string.month_this_month),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (enabled) {
                    HabitsTheme.colors.text.secondary
                } else {
                    HabitsTheme.colors.text.disabled
                },
        )
    }
}

private val previewMonth: YearMonth = YearMonth.from(SampleHabits.referenceDate)

@Preview(name = "Month header", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun MonthHeaderPreview() {
    HabitsTheme {
        MonthHeader(
            state = MonthUiState.Content(month = previewMonth, habits = emptyList(), isCurrentMonth = true),
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
            state =
                MonthUiState.Content(
                    month = previewMonth.minusMonths(NINE_MONTHS),
                    habits = emptyList(),
                    isCurrentMonth = false,
                ),
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
