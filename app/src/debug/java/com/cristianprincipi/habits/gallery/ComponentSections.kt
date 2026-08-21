package com.cristianprincipi.habits.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.core.designsystem.component.EmptyState
import com.cristianprincipi.habits.core.designsystem.component.HabitChip
import com.cristianprincipi.habits.core.designsystem.component.HabitIcon
import com.cristianprincipi.habits.core.designsystem.component.HabitIconSize
import com.cristianprincipi.habits.core.designsystem.component.MonthDotGrid
import com.cristianprincipi.habits.core.designsystem.component.MonthHabitCard
import com.cristianprincipi.habits.core.designsystem.component.NavArrows
import com.cristianprincipi.habits.core.designsystem.component.NumeralStyle
import com.cristianprincipi.habits.core.designsystem.component.StatTile
import com.cristianprincipi.habits.core.designsystem.component.StatTileIcon
import com.cristianprincipi.habits.core.designsystem.component.WeekHabitCard
import com.cristianprincipi.habits.core.designsystem.component.WeekPills
import com.cristianprincipi.habits.core.designsystem.component.YearDotGrid
import com.cristianprincipi.habits.core.designsystem.component.YearHabitCard
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/*
 * The component half of the gallery. Everything is live: the chips select, the pills toggle, the
 * arrows step. A static screenshot of these would prove they draw, which the tests already prove —
 * what is worth having in the hand is how they respond.
 */

@Composable
fun HabitIconSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        GalleryLabel("Small, 32dp — the Month card")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            SampleHabits.forEach { HabitIcon(it.emoji, it.colour(), size = HabitIconSize.SMALL) }
        }
        GalleryLabel("Large, 40dp — the Week and Year cards")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            SampleHabits.forEach { HabitIcon(it.emoji, it.colour()) }
        }
    }
}

@Composable
fun ChipsAndNavSection() {
    var selected by remember { mutableIntStateOf(0) }
    var month by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        GalleryLabel("Habit chips — tap to select; press and hold for the scale")
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
        ) {
            SampleHabits.forEachIndexed { index, habit ->
                HabitChip(
                    emoji = habit.emoji,
                    name = habit.name,
                    habitColor = habit.colour(),
                    selected = index == selected,
                    onClick = { selected = index },
                )
            }
        }

        GalleryLabel("Nav arrows — 18dp glyph, 32dp button, 48dp target")
        NavArrows(
            label = MONTHS[((month % MONTHS.size) + MONTHS.size) % MONTHS.size],
            previousContentDescription = "Previous month",
            nextContentDescription = "Next month",
            onPrevious = { month-- },
            onNext = { month++ },
        )
    }
}

@Composable
fun WeekPillsSection() {
    var week by remember { mutableStateOf(SampleWeek) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        GalleryLabel("Live — tap a pill. Friday carries the today ring, complete or not")
        WeekPills(
            days = week,
            dayLabels = DayLabels,
            dayContentDescriptions = DayLabels,
            habitColor = HabitsTheme.colors.habit.green,
            todayIndex = TODAY,
            onToggle = { index ->
                week =
                    week.toMutableList().also {
                        it[index] = if (it[index] == DayStatus.COMPLETED) DayStatus.INCOMPLETE else DayStatus.COMPLETED
                    }
            },
        )

        GalleryLabel("All complete")
        StaticWeek(List(SEVEN) { DayStatus.COMPLETED }, HabitsTheme.colors.habit.blue, todayIndex = -1)

        GalleryLabel("All incomplete")
        StaticWeek(List(SEVEN) { DayStatus.INCOMPLETE }, HabitsTheme.colors.habit.orange, todayIndex = 0)

        GalleryLabel("Entirely in the future")
        StaticWeek(List(SEVEN) { DayStatus.FUTURE }, HabitsTheme.colors.habit.purple, todayIndex = -1)
    }
}

@Composable
private fun StaticWeek(
    days: List<DayStatus>,
    colour: androidx.compose.ui.graphics.Color,
    todayIndex: Int,
) {
    WeekPills(
        days = days,
        dayLabels = DayLabels,
        dayContentDescriptions = DayLabels,
        habitColor = colour,
        todayIndex = todayIndex,
        onToggle = {},
    )
}

@Composable
fun DotGridSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        GalleryLabel(
            "Month — completed, incomplete and future, ragged last row. A cell is width ÷ 7, so " +
                "all three below are drawn at one Month card's width",
        )
        AtMonthCardWidth {
            MonthDotGrid(days = GalleryMonth, habitColor = HabitsTheme.colors.habit.blue)
        }

        GalleryLabel("Month — a single day, the first day a habit exists")
        AtMonthCardWidth {
            MonthDotGrid(days = listOf(DayStatus.COMPLETED), habitColor = HabitsTheme.colors.habit.green)
        }

        GalleryLabel("Month — empty, which must not divide by zero")
        AtMonthCardWidth {
            MonthDotGrid(days = emptyList(), habitColor = HabitsTheme.colors.habit.orange)
        }

        GalleryLabel("Year at 4dp — the Year card. Scrolls, and starts at today")
        YearDotGrid(days = GalleryYear, habitColor = HabitsTheme.colors.habit.orange, dotSize = Sizing.yearDotSmall)

        GalleryLabel("Year at 5dp — the default")
        YearDotGrid(days = GalleryYear, habitColor = HabitsTheme.colors.habit.green, dotSize = Sizing.yearDot)

        GalleryLabel("Year at 6dp — the Stats overview card")
        YearDotGrid(days = GalleryYear, habitColor = HabitsTheme.colors.habit.purple, dotSize = Sizing.yearDotLarge)
        GalleryNote(
            "The faintest band on the left of each year is BEFORE_START at 6% — the Year grid is the " +
                "only one that separates \"before this habit existed\" from an ordinary missed day.",
        )
    }
}

@Composable
fun StatTileSection() {
    val purple = HabitsTheme.colors.habit.purple

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        GalleryLabel("A — DM Sans, the design's specified 700 weight")
        StatGrid(NumeralStyle.SANS, purple)

        GalleryLabel("B — DM Mono, stable digit widths, Medium 500")
        StatGrid(NumeralStyle.MONO, purple)

        GalleryNote(
            "Same numbers, same sizes, same tiles — only the typeface and weight differ. A is the " +
                "design as specified and its digits shift as values change, because DM Sans has no " +
                "tabular figures and \"1\" is about half the width of \"0\". B holds every digit at the " +
                "same width, but DM Mono has no bold, so it renders 500 against the design's 700. " +
                "Both numbers sit in fixed-size tiles either way: what moves is glyphs inside a stable " +
                "box, never the layout, and they change about once a day.",
        )
    }
}

@Composable
private fun StatGrid(
    numeralStyle: NumeralStyle,
    colour: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            StatTile("Completed days", "111", colour, Modifier.weight(1f), numeralStyle) {
                StatTileIcon(Lucide.Hash, colour)
            }
            StatTile("Completion rate", "84%", colour, Modifier.weight(1f), numeralStyle) {
                StatTileIcon(Lucide.Percent, colour)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            StatTile("Current streak", "10", colour, Modifier.weight(1f), numeralStyle) {
                StatTileIcon(Lucide.Flame, colour)
            }
            StatTile("Best streak", "108", colour, Modifier.weight(1f), numeralStyle) {
                StatTileIcon(Lucide.Trophy, colour)
            }
        }
    }
}

@Composable
fun HabitCardSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        GalleryLabel("Month cards — two across, as the Month view lays them out")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            MonthHabitCard(
                name = "Learn Guitar",
                description = "15 min practice",
                emoji = "🎸",
                habitColor = HabitsTheme.colors.habit.blue,
                days = GalleryMonth,
                modifier = Modifier.weight(1f),
            )
            MonthHabitCard(
                name = "Read before sleep",
                description = "A long description that has to truncate",
                emoji = "📖",
                habitColor = HabitsTheme.colors.habit.purple,
                days = GalleryMonth,
                modifier = Modifier.weight(1f),
            )
        }

        GalleryLabel("Week card")
        WeekHabitCard(
            name = "Drink water",
            description = "8 glasses a day",
            emoji = "💧",
            habitColor = HabitsTheme.colors.habit.green,
            days = SampleWeek,
            dayLabels = DayLabels,
            dayContentDescriptions = DayLabels,
            todayIndex = TODAY,
            onToggleDay = {},
            modifier = Modifier.fillMaxWidth(),
        )

        GalleryLabel("Year card — numerals A, DM Sans")
        YearCard(NumeralStyle.SANS)

        GalleryLabel("Year card — numerals B, DM Mono")
        YearCard(NumeralStyle.MONO)
    }
}

@Composable
private fun YearCard(numeralStyle: NumeralStyle) {
    YearHabitCard(
        name = "Morning Yoga",
        description = "20 min flow",
        emoji = "🧘",
        habitColor = HabitsTheme.colors.habit.orange,
        completionLabel = "84%",
        completionEyebrow = "COMPLETION",
        days = GalleryYear,
        modifier = Modifier.fillMaxWidth(),
        numeralStyle = numeralStyle,
    )
}

@Composable
fun EmptyStateSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GalleryLabel("Undesigned — the prototype has no empty state anywhere (ADR-008)")
        EmptyState(
            emoji = "🌱",
            title = "No habits yet",
            body = "Habits live in your Notion database. Add one there and it will appear here after the next sync.",
        )
        EmptyState(
            emoji = "🔌",
            title = "Not connected",
            body = "Connect your Notion workspace to see your habits.",
            actionLabel = "Connect to Notion",
            onAction = {},
        )
    }
}

// --- Sample data -------------------------------------------------------------------------------

private class SampleHabit(
    val emoji: String,
    val name: String,
    val colourIndex: Int,
)

@Composable
private fun SampleHabit.colour() = HabitsTheme.colors.habit.assignable[colourIndex]

private val SampleHabits =
    listOf(
        SampleHabit("🎸", "Guitar", 0),
        SampleHabit("💧", "Water", 1),
        SampleHabit("🧘", "Yoga", 2),
        SampleHabit("📖", "Read", 3),
    )

private val DayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val SampleWeek =
    listOf(
        DayStatus.COMPLETED,
        DayStatus.COMPLETED,
        DayStatus.INCOMPLETE,
        DayStatus.COMPLETED,
        DayStatus.INCOMPLETE,
        DayStatus.FUTURE,
        DayStatus.FUTURE,
    )

/** August 2026: five leading blanks, 31 days, today the 21st. */
private val GalleryMonth =
    List(FIVE) { DayStatus.PADDING } +
        List(THIRTY_ONE) { index ->
            val day = index + 1
            when {
                day > TWENTY_ONE -> DayStatus.FUTURE
                day % 3 == 0 -> DayStatus.INCOMPLETE
                else -> DayStatus.COMPLETED
            }
        }

/** A trailing year on a habit that started two months in, so all four states appear. */
private val GalleryYear =
    List(YEAR) { index ->
        when {
            index < STARTED -> DayStatus.BEFORE_START
            index % 4 == 0 -> DayStatus.INCOMPLETE
            else -> DayStatus.COMPLETED
        }
    }

private val MONTHS = listOf("August 2026", "September 2026", "October 2026", "November 2026")

private const val SEVEN = 7
private const val MONTH_CARD_FRACTION = 0.5f
private const val TODAY = 4
private const val FIVE = 5
private const val THIRTY_ONE = 31
private const val TWENTY_ONE = 21
private const val YEAR = 365
private const val STARTED = 60

/**
 * Half the gallery's width, which is roughly what the Month view's two-across layout gives a card.
 *
 * A `MonthDotGrid` sizes its cells at width ÷ 7, so handed the whole screen it draws 50dp squares —
 * a magnification of the component rather than a specimen of it. The Month cards further down show
 * the same grid in its real container; these three exist for the data cases, and have to be the
 * right size to be worth comparing against the design.
 */
@Composable
private fun AtMonthCardWidth(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(MONTH_CARD_FRACTION)) { content() }
}
