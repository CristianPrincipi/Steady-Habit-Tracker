package com.cristianprincipi.habits.feature.week

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
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.datetime.weekRangeLabel
import com.cristianprincipi.habits.core.designsystem.component.NavArrows
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import java.util.Locale

/**
 * The Week screen's header — `docs/design-spec.md` §1 and §3.
 *
 * Two rows: the title, and the `‹ Aug 17 – Aug 23 ›` sub-nav beneath it. Pinned above the list
 * rather than scrolling with it, which is what §1's diagram shows.
 *
 * ### ⚠ The design's `+` button is not here, and never will be
 *
 * §1 puts a `+` on the Month, Week and Year headers and records that it is **inert in the
 * prototype**. **ADR-010 is why it stays that way**: a habit *is* a checkbox property in the user's
 * Notion database, so creating one is a schema change this app deliberately cannot make. A `+` here
 * would be a control that must never work.
 *
 * ⚠ Something is still owed to `ManageHabits`, which `HabitsNavHost` notes is unreachable "until the
 * header's `+` exists". That screen is a **read-only** list (ADR-010 again), so whatever reaches it
 * wants a different glyph than a plus. **M7.5's to settle.**
 *
 * ### The top inset is the design's `pt-10`
 *
 * `docs/tokens.md` §7 already settled this: the prototype's 40px header padding *is* the status-bar
 * inset on Android, and the shell supplies it (ADR-063). Nothing here adds top padding, and there is
 * deliberately no token for it.
 */
@Composable
fun WeekHeader(
    week: WeekUiState.Content?,
    onEvent: (WeekEvent) -> Unit,
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
                text = stringResource(R.string.screen_title_week),
                style = MaterialTheme.typography.headlineSmall,
                color = HabitsTheme.colors.text.primary,
            )
            if (week != null) {
                ThisWeekButton(
                    enabled = !week.isCurrentWeek,
                    onClick = { onEvent(WeekEvent.ThisWeekPressed) },
                )
            }
        }

        // No week, no paging. Loading has nothing to page through yet, and Empty is week-independent
        // (`WeekUiState.Empty`) — moving a label while the screen underneath cannot change would be
        // a control that does nothing.
        if (week != null) {
            NavArrows(
                label =
                    weekRangeLabel(
                        weekStart = week.weekStart,
                        weekEndInclusive = week.weekEndInclusive,
                        locale = locale,
                        currentYear = week.currentYear,
                    ),
                previousContentDescription = stringResource(R.string.week_previous),
                nextContentDescription = stringResource(R.string.week_next),
                onPrevious = { onEvent(WeekEvent.PreviousWeekPressed) },
                onNext = { onEvent(WeekEvent.NextWeekPressed) },
            )
        }
    }
}

/**
 * Back to the week containing today.
 *
 * ⭐ **Not in the prototype, and added deliberately.** `NavArrows`' own KDoc notes that the design
 * offers no jump-to-today and calls it "the design's choice, not an omission" — which was fair when
 * the arrows were inert. Once they page, a user who has gone back eleven weeks has to count their
 * way home, on the landing screen of a daily app. `Plan.md` M6.4 asks for the affordance.
 *
 * ⚠ **Disabled in place rather than hidden**, which is the user's stated preference (ADR-060, where
 * hiding a control let a destructive button reflow into the pixels a second tap was headed for).
 * Nothing so dangerous is nearby here, but the same reasoning holds: the control's presence is what
 * tells you the app can bring you back, and a row that changes width as you page is a row that
 * moves the title. **Whether a permanently-visible disabled button is right on the app's landing
 * screen is a judgement for M6.11.**
 */
@Composable
private fun ThisWeekButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text = stringResource(R.string.week_this_week),
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

@Preview(name = "Header — this week", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun WeekHeaderCurrentPreview() {
    HabitsTheme {
        WeekHeader(week = previewWeek(isCurrentWeek = true), onEvent = {}, locale = Locale.UK)
    }
}

@Preview(name = "Header — paged away", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun WeekHeaderPagedPreview() {
    HabitsTheme {
        WeekHeader(week = previewWeek(isCurrentWeek = false), onEvent = {}, locale = Locale.UK)
    }
}

@Preview(name = "Header — no week", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun WeekHeaderEmptyPreview() {
    HabitsTheme {
        WeekHeader(week = null, onEvent = {}, locale = Locale.UK)
    }
}

@Preview(name = "Header — another year", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun WeekHeaderOtherYearPreview() {
    HabitsTheme {
        WeekHeader(
            // Paged back past New Year, so the label carries the year it belongs to (M6.11).
            week = previewWeek(isCurrentWeek = false, weeksBack = 40),
            onEvent = {},
            locale = Locale.UK,
        )
    }
}

/** `SampleHabits.referenceDate` rather than a literal, so one date governs every preview in M6. */
private fun previewWeek(
    isCurrentWeek: Boolean,
    weeksBack: Long = 0,
) = WeekUiState.Content(
    weekStart = startOfWeek(SampleHabits.referenceDate).minusWeeks(weeksBack),
    habits = emptyList(),
    isCurrentWeek = isCurrentWeek,
    currentYear = SampleHabits.referenceDate.year,
)
