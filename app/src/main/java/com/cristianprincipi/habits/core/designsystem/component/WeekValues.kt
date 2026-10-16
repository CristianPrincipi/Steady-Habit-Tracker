package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.datetime.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.designsystem.theme.DayOpacity
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * A week of numbers, one under each weekday — the Week screen's measure row (M16.1).
 *
 * [WeekPills]' read-only twin, laid out by the same arithmetic so a value sits exactly under the
 * pill for its day: the same seven columns, the same gap, the same [WeekdayHeadings], the same 36dp
 * slot and the same ring on today. **Nothing here is tappable yet** — editing a value is M16.2, and
 * a cell that looked like a button and did nothing would be worse than one that plainly is not.
 *
 * ⚠ **One font size for the whole row**, through [fittedFontSize], for the weekday initials' reason
 * (M13.3): `82.9` is about 26dp at 12sp in a ~44dp column and fits, but at a large font scale it
 * would not, and a row where only some numbers shrank reads as a rendering fault.
 *
 * @param texts seven entries, Monday first: the formatted value, the caller's dash for a missing
 *   day, or anything for a future one (it is not drawn).
 * @param statuses seven entries, parallel to [texts].
 * @param contentDescriptions what a screen reader says per cell — the day and its value.
 * @param todayIndex which cell gets the ring, or `-1` for a week that does not contain today.
 */
@Composable
fun WeekValues(
    texts: List<String>,
    statuses: List<ValueStatus>,
    dayLabels: List<String>,
    contentDescriptions: List<String>,
    color: Color,
    todayIndex: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columnWidth = (maxWidth - Spacing.weekPillGap * (DAYS_PER_WEEK - 1)) / DAYS_PER_WEEK
        val valueStyle = MaterialTheme.typography.labelMedium
        val drawn = texts.filterIndexed { index, _ -> statuses.getOrNull(index) != ValueStatus.FUTURE }
        val valueSize =
            fittedFontSize(texts = drawn, style = valueStyle, available = columnWidth - Spacing.valueCellPadding * 2)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.weekPillGap)) {
            WeekdayHeadings(dayLabels = dayLabels, columnWidth = columnWidth)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.weekPillGap)) {
                statuses.forEachIndexed { index, status ->
                    ValueCell(
                        text = texts.getOrElse(index) { "" },
                        status = status,
                        color = color,
                        isToday = index == todayIndex,
                        contentDescription = contentDescriptions.getOrElse(index) { "" },
                        modifier = Modifier.weight(1f),
                        textStyle = valueStyle.copy(fontSize = valueSize),
                    )
                }
            }
        }
    }
}

/**
 * One day's slot: the pill's shape and tint, with a number in it instead of a tick.
 *
 * ⚠ **The tint is the *unticked* pill's for every cell**, recorded or not. Filling a recorded day in
 * full colour would borrow the pill's "done" — and a number is not an achievement, which is the
 * same reason a missing one is a dash rather than an "X". The text carries the difference instead.
 */
@Composable
private fun ValueCell(
    text: String,
    status: ValueStatus,
    color: Color,
    isToday: Boolean,
    contentDescription: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val tint = HabitsTheme.colors.tint.weekPillIncomplete
    val fill =
        when (status) {
            ValueStatus.FUTURE -> color.copy(alpha = tint * DayOpacity.WEEK_FUTURE)
            ValueStatus.RECORDED, ValueStatus.MISSING -> color.copy(alpha = tint)
        }
    val textColor =
        when (status) {
            ValueStatus.RECORDED -> HabitsTheme.colors.text.primary
            ValueStatus.MISSING, ValueStatus.FUTURE -> HabitsTheme.colors.text.helper
        }

    Box(
        modifier =
            modifier
                .heightIn(min = Sizing.weekPillHeight)
                .background(color = fill, shape = shape)
                .then(if (isToday) Modifier.border(Sizing.todayRing, color, shape) else Modifier)
                .padding(horizontal = Spacing.valueCellPadding)
                // Merged, so a screen reader stops once per day and says the description — the
                // measure, the date and the value with its unit — rather than also reading the bare
                // number inside as a second stop.
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (status != ValueStatus.FUTURE) {
            Text(
                text = text,
                style = textStyle,
                color = textColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun WeekValuesPreview() {
    HabitsTheme {
        WeekValues(
            texts = listOf("82.9", "–", "82.6", "82.4", "–", "", ""),
            statuses =
                listOf(
                    ValueStatus.RECORDED,
                    ValueStatus.MISSING,
                    ValueStatus.RECORDED,
                    ValueStatus.RECORDED,
                    ValueStatus.MISSING,
                    ValueStatus.FUTURE,
                    ValueStatus.FUTURE,
                ),
            dayLabels = PREVIEW_DAY_LABELS,
            contentDescriptions = PREVIEW_DAY_LABELS,
            color = HabitsTheme.colors.habit.green,
            todayIndex = 4,
            modifier = Modifier.padding(Spacing.cardPadding),
        )
    }
}
