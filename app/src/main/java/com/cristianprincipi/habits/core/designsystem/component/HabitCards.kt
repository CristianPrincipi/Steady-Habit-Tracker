package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
 *
 * Since M16.1 a fourth lives here too — [WeekMeasureCard], a number's week — because it is the Week
 * card with values for pills and needs the same header, which is private to this file.
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
 * A tracked number's week — Weight, Sleep — under the habit cards on Week (M16.1).
 *
 * [WeekHabitCard]'s shape with [WeekValues] where the pills would be, so the two read as one list
 * and a value sits under the pill for its day. **Undesigned**: the prototype has no number on this
 * screen, so the header line, the cell tint and the dash are this app's, and were shown to the user.
 *
 * @param description the week's average, already formatted, or the sentence for a week without one.
 */
@Composable
fun WeekMeasureCard(
    name: String,
    description: String,
    emoji: String,
    color: Color,
    texts: List<String>,
    statuses: List<ValueStatus>,
    dayLabels: List<String>,
    dayContentDescriptions: List<String>,
    todayIndex: Int,
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
            habitColor = color,
            iconSize = HabitIconSize.LARGE,
            nameStyle = MaterialTheme.typography.titleSmall,
            descriptionStyle = MaterialTheme.typography.bodySmall,
            descriptionColor = HabitsTheme.colors.text.secondary,
        )
        WeekValues(
            texts = texts,
            statuses = statuses,
            dayLabels = dayLabels,
            contentDescriptions = dayContentDescriptions,
            color = color,
            todayIndex = todayIndex,
        )
    }
}

/**
 * The Year card: the header plus a right-aligned completion figure, over a year of days.
 *
 * The percentage sits in its own column with the `COMPLETION` eyebrow beneath it — a fixed block at
 * the end of the row, so a value going from `9%` to `100%` moves glyphs inside the block and never
 * the header beside it.
 *
 * @param completionContentDescription what the block above says as **one** phrase. ⚠⚠ **The eyebrow
 *   is below the figure, and reading order is drawing order** — so this card was the one place in
 *   the app that announced a value and *then* what it measured: "87%", then "COMPLETION" (M13.2).
 *   The design is not at fault and does not move; only the ear needs the two the other way round.
 */
@Composable
fun YearHabitCard(
    name: String,
    description: String,
    emoji: String,
    habitColor: Color,
    completionLabel: String,
    completionEyebrow: String,
    completionContentDescription: String,
    days: List<DayStatus>,
    modifier: Modifier = Modifier,
) {
    HabitsCard(modifier = modifier) {
        // ⚠⚠ M13.3 — **at 200% font this row lost the habit's name.** The completion block has no
        // width cap, so as it grew the `weight(1f)` header was squeezed until "Journal" read "Jou…"
        // and "199 of 261" read "199 …". The name is the one thing on this card a person needs, and
        // it was the thing that went — the block beside it kept every character of "COMPLETION".
        //
        // ⭐ **So the row becomes a column when the eyebrow no longer fits its share.** Measured
        // rather than thresholded: `everyWordFits` asks whether the widest word can be drawn in the
        // fraction of the card this block is allowed, which follows a copy change and a translation
        // without anyone editing a number. Nothing moves at any scale where the design's row fits.
        BoxWithConstraints {
            val eyebrowShare = maxWidth * COMPLETION_WIDTH_SHARE
            val fitsBeside =
                everyWordFits(completionEyebrow, HabitsTheme.textStyles.eyebrowSmall, eyebrowShare) &&
                    everyWordFits(completionLabel, HabitsTheme.textStyles.metricValue, eyebrowShare)

            YearCardHeadline(
                name = name,
                description = description,
                emoji = emoji,
                habitColor = habitColor,
                completionLabel = completionLabel,
                completionEyebrow = completionEyebrow,
                completionContentDescription = completionContentDescription,
                stacked = !fitsBeside,
            )
        }
        YearDotGrid(days = days, habitColor = habitColor, dotSize = Sizing.yearDotSmall)
    }
}

/**
 * The Year card's top block: the header and the completion figure, side by side or stacked.
 *
 * @param stacked when the row cannot hold both — see [YearHabitCard]. Stacked, the figure keeps its
 *   end alignment and simply sits under the name instead of beside it, so the card reads the same
 *   way down the page and nothing is truncated on either side.
 */
@Composable
private fun YearCardHeadline(
    name: String,
    description: String,
    emoji: String,
    habitColor: Color,
    completionLabel: String,
    completionEyebrow: String,
    completionContentDescription: String,
    stacked: Boolean,
) {
    val header: @Composable (Modifier) -> Unit = { headerModifier ->
        HabitCardHeader(
            name = name,
            description = description,
            emoji = emoji,
            habitColor = habitColor,
            iconSize = HabitIconSize.LARGE,
            nameStyle = MaterialTheme.typography.titleSmall,
            descriptionStyle = MaterialTheme.typography.bodySmall,
            descriptionColor = HabitsTheme.colors.text.secondary,
            modifier = headerModifier,
        )
    }
    val completion: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
        // ⭐ Merged **with** a description rather than cleared and replaced: an explicit
        // `contentDescription` is what a screen reader announces, while the two `Text`s stay in
        // the merged node's config — so the eyebrow is still findable by the word it prints, and
        // a test that looks for `COMPLETION` is testing the screen rather than this decision.
        Column(
            horizontalAlignment = alignment,
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = completionContentDescription
                },
        ) {
            Text(
                text = completionLabel,
                style = HabitsTheme.textStyles.metricValue,
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

    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            header(Modifier.fillMaxWidth())
            completion(Alignment.Start)
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            header(Modifier.weight(1f))
            completion(Alignment.End)
        }
    }
}

/**
 * Icon, name and description — the row all three cards open with, at three different scales.
 *
 * ⚠⚠ **One merging boundary, and it fixes all three cards at once (M13.2).** Name and description are
 * two `Text`s in a `Column`, and a `Column` merges nothing — so a screen reader stopped twice per
 * card and the second stop was a bare number: "Read", then "6 of 7". **A figure heard on its own is
 * a figure about nothing**, and touch exploration lands on it directly rather than arriving from the
 * name above it. Merged, the card opens with "Read, 6 of 7" — one stop, in the order the eye reads.
 *
 * ⭐ The seven pills below are unaffected: a clickable is its own merging boundary, so they stay
 * seven separate stops, which is exactly right for seven separate things to tap.
 */
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
        modifier = modifier.semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(Spacing.weekPillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cleared in [HabitIcon], so the merge does not pick the emoji up and read it twice — the
        // name it sits beside is already the thing it stands for.
        HabitIcon(emoji = emoji, name = name, habitColor = habitColor, size = iconSize)
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
private fun WeekMeasureCardPreview() {
    HabitsTheme {
        WeekMeasureCard(
            name = "Weight",
            description = "Average 82.6 kg",
            emoji = "⚖️",
            color = HabitsTheme.colors.habit.green,
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
            dayContentDescriptions = PREVIEW_DAY_LABELS,
            todayIndex = 4,
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
            completionContentDescription = "84% completion",
            days = previewYear(),
            modifier = Modifier.padding(Spacing.screenPadding),
        )
    }
}

/**
 * How much of the Year card the completion block may claim before the row stops being a row.
 *
 * ⚠ **A share rather than a `Dp`**, because the question is about balance and not about a size: the
 * header beside it keeps the rest, and a fixed width would give a narrow card away entirely and a
 * wide one too little. 0.4 leaves the name the clear majority at every scale where the row survives.
 */
private const val COMPLETION_WIDTH_SHARE = 0.4f
