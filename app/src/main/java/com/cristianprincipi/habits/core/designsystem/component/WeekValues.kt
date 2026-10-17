package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
 * [WeekPills]' twin, laid out by the same arithmetic so a value sits exactly under the pill for its
 * day: the same seven columns, the same gap, the same [WeekdayHeadings], the same 36dp slot and the
 * same ring on today.
 *
 * ⭐ **An [editable] cell is tappable since M16.2b**, and borrows the pill's affordances rather than
 * inventing new ones: a 48dp target around the drawn 36dp, the unticked pill's neutral hairline on
 * every editable day that is not today, and the pill's press scale. A day that cannot be edited —
 * one that has not happened — keeps no outline and no click, so nothing looks like a button and does
 * nothing.
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
 * @param editable seven entries, parallel to [texts]; a missing entry is not editable, so a caller
 *   that passes none draws the read-only row M16.1 shipped.
 * @param editLabel what TalkBack says a tap does ("edit"), for the cells that have one.
 * @param onCellTap fired with the index of an editable cell.
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
    editable: List<Boolean> = emptyList(),
    editLabel: String? = null,
    onCellTap: (Int) -> Unit = {},
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
                        onTap = if (editable.getOrElse(index) { false }) ({ onCellTap(index) }) else null,
                        editLabel = editLabel,
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
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    editLabel: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
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
    // M13.4's reason, inherited from the unticked pill: at this tint a cell is ~1.2:1 against its
    // card, and an editable control needs 3:1 on what identifies it. Today keeps its coloured ring.
    val outline =
        HabitsTheme.colors.text.primary
            .copy(alpha = HabitsTheme.colors.tint.weekPillOutline)

    // The outer box is the 48dp target, the inner one the 36dp the row draws — `WeekPills`' DayPill.
    Box(
        modifier =
            modifier
                .heightIn(min = if (onTap != null) Sizing.minimumTouchTarget else Sizing.weekPillHeight)
                .then(
                    if (onTap != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClickLabel = editLabel,
                            role = Role.Button,
                            onClick = onTap,
                        )
                    } else {
                        Modifier
                    },
                )
                // Merged, so a screen reader stops once per day and says the description — the
                // measure, the date and the value with its unit — rather than also reading the bare
                // number inside as a second stop.
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Sizing.weekPillHeight)
                    .then(if (onTap != null) Modifier.pressScale(interactionSource, CELL_PRESSED_SCALE) else Modifier)
                    .background(color = fill, shape = shape)
                    .then(
                        when {
                            isToday -> Modifier.border(Sizing.todayRing, color, shape)
                            onTap != null -> Modifier.border(Sizing.todayRing, outline, shape)
                            else -> Modifier
                        },
                    ).padding(horizontal = Spacing.valueCellPadding),
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
}

/** The pill's `active:scale-90`, so a cell presses the way the pill above it does. */
private const val CELL_PRESSED_SCALE = 0.90f

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
            editable = listOf(true, true, true, true, true, false, false),
        )
    }
}
