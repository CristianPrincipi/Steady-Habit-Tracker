package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/*
 * The three habit cards — Month, Week and Year (`docs/design-spec.md §2`, `§3`, `§4`).
 *
 * They share a shape and a header and diverge below it, so they share [HabitsCard] and
 * [HabitCardHeader] and nothing else. Every one takes primitives rather than a `Habit`: `domain/`
 * does not exist until M3, and a design system that does not know about the domain is one that can
 * be previewed and tested without it.
 */

/**
 * The Month card: the tightest of the three, at 14dp padding with a 32dp icon and a 12sp name.
 *
 * Name and description both truncate to one line. The design puts two of these side by side, so
 * there is no room for either to wrap.
 */
@Composable
fun MonthHabitCard(
    name: String,
    description: String,
    emoji: String,
    habitColor: Color,
    days: List<DayStatus>,
    modifier: Modifier = Modifier,
) {
    HabitsCard(modifier = modifier, contentPadding = Spacing.cardPaddingCompact) {
        HabitCardHeader(
            name = name,
            description = description,
            emoji = emoji,
            habitColor = habitColor,
            iconSize = HabitIconSize.SMALL,
            nameStyle = MaterialTheme.typography.labelMedium,
            descriptionStyle = MaterialTheme.typography.labelSmall,
            descriptionColor = HabitsTheme.colors.text.tertiary,
        )
        MonthDotGrid(days = days, habitColor = habitColor)
    }
}

/**
 * The Week card: a 40dp icon, a 14sp name, and the seven pills that check a habit off.
 *
 * Spaced at [Spacing.cardContentGap] rather than the usual card gap — the design gives this one card
 * more internal air, because the pill row needs separating from the header.
 */
@Composable
fun WeekHabitCard(
    name: String,
    description: String,
    emoji: String,
    habitColor: Color,
    days: List<DayStatus>,
    dayLabels: List<String>,
    dayContentDescriptions: List<String>,
    todayIndex: Int,
    onToggleDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    HabitsCard(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.cardContentGap),
    ) {
        HabitCardHeader(
            name = name,
            description = description,
            emoji = emoji,
            habitColor = habitColor,
            iconSize = HabitIconSize.LARGE,
            nameStyle = MaterialTheme.typography.titleSmall,
            descriptionStyle = MaterialTheme.typography.bodySmall,
            descriptionColor = HabitsTheme.colors.text.secondary,
        )
        WeekPills(
            days = days,
            dayLabels = dayLabels,
            dayContentDescriptions = dayContentDescriptions,
            habitColor = habitColor,
            todayIndex = todayIndex,
            onToggle = onToggleDay,
        )
    }
}

/**
 * The Year card: the header plus a right-aligned completion figure, over a year of days.
 *
 * The percentage sits in its own column with the `COMPLETION` eyebrow beneath it — a fixed block at
 * the end of the row, so a value going from `9%` to `100%` moves glyphs inside the block and never
 * the header beside it.
 */
@Composable
fun YearHabitCard(
    name: String,
    description: String,
    emoji: String,
    habitColor: Color,
    completionLabel: String,
    completionEyebrow: String,
    days: List<DayStatus>,
    modifier: Modifier = Modifier,
    numeralStyle: NumeralStyle = NumeralStyle.SANS,
) {
    HabitsCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HabitCardHeader(
                name = name,
                description = description,
                emoji = emoji,
                habitColor = habitColor,
                iconSize = HabitIconSize.LARGE,
                nameStyle = MaterialTheme.typography.titleSmall,
                descriptionStyle = MaterialTheme.typography.bodySmall,
                descriptionColor = HabitsTheme.colors.text.secondary,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = completionLabel,
                    style = numeralStyle.metricValue(),
                    color = HabitsTheme.colors.text.primary,
                )
                Text(
                    text = completionEyebrow,
                    style = HabitsTheme.textStyles.eyebrowSmall,
                    color = HabitsTheme.colors.text.eyebrow,
                    textAlign = TextAlign.End,
                )
            }
        }
        YearDotGrid(days = days, habitColor = habitColor, dotSize = Sizing.yearDotSmall)
    }
}

/** Icon, name and description — the row all three cards open with, at three different scales. */
@Composable
private fun HabitCardHeader(
    name: String,
    description: String,
    emoji: String,
    habitColor: Color,
    iconSize: HabitIconSize,
    nameStyle: androidx.compose.ui.text.TextStyle,
    descriptionStyle: androidx.compose.ui.text.TextStyle,
    descriptionColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.weekPillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HabitIcon(emoji = emoji, habitColor = habitColor, size = iconSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = nameStyle,
                color = HabitsTheme.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = descriptionStyle,
                color = descriptionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 200)
@Composable
private fun MonthHabitCardPreview() {
    HabitsTheme {
        MonthHabitCard(
            name = "Learn Guitar",
            description = "15 min practice",
            emoji = "🎸",
            habitColor = HabitsTheme.colors.habit.blue,
            days = previewMonth(),
            modifier = Modifier.padding(Spacing.cardGap),
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun WeekHabitCardPreview() {
    HabitsTheme {
        WeekHabitCard(
            name = "Drink water",
            description = "8 glasses a day",
            emoji = "💧",
            habitColor = HabitsTheme.colors.habit.green,
            days = previewWeek(),
            dayLabels = PREVIEW_DAY_LABELS,
            dayContentDescriptions = PREVIEW_DAY_LABELS,
            todayIndex = 4,
            onToggleDay = {},
            modifier = Modifier.padding(Spacing.screenPadding),
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun YearHabitCardPreview() {
    HabitsTheme {
        YearHabitCard(
            name = "Morning Yoga",
            description = "20 min flow",
            emoji = "🧘",
            habitColor = HabitsTheme.colors.habit.orange,
            completionLabel = "84%",
            completionEyebrow = "COMPLETION",
            days = previewYear(),
            modifier = Modifier.padding(Spacing.screenPadding),
        )
    }
}
