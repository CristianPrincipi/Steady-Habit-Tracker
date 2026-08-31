package com.cristianprincipi.habits.feature.managehabits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.ColorSwatchRow
import com.cristianprincipi.habits.core.designsystem.component.HabitIcon
import com.cristianprincipi.habits.core.designsystem.component.HabitsCard
import com.cristianprincipi.habits.core.designsystem.component.SecondaryButton
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor

/**
 * The habit list — **read-only, except for the colour** (ADR-010).
 *
 * ⚠ **Undesigned: this screen is in no Figma frame** (`docs/design-spec.md` §0), so everything here
 * comes from M2's design system, and the user judges it on a device rather than from this file.
 *
 * ### What the screen has to say, and why it says it here
 *
 * A user arriving at a list of their habits will look for an "add" button, and there is none — a
 * habit *is* a checkbox column in their Notion database, so creating one is a schema change this app
 * deliberately cannot make. Rather than an absence they have to interpret, the card at the top says
 * where habits come from and offers the way there.
 *
 * ⚠ **It does not promise that a column added in Notion becomes a habit.** It does not: only the
 * saved mapping decides which columns are habits (ADR-079), because `Weight`, `Sleep`, `Area` and
 * `Progress` live in the same database. **M7.8 builds the route that adopts a new column**, and owns
 * the sentence describing it — it links from this screen.
 *
 * ### ⭐ A habit that has left the mapped set is not on this list — the user's call (ADR-081)
 *
 * It was, until 2026-08-31, carrying a note that said its column was gone. **The list is the
 * current habit set and nothing else** now: the row and its history stay in Room, invisible, and
 * return in place if the property does. Nothing here announces the absence, and nothing needs to —
 * a column deleted in Notion *pauses* sync (`NotionMappingProblem.HabitMissing`) rather than
 * quietly thinning this list, and sync status is where a paused sync says so.
 */
@Composable
fun ManageHabitsContent(
    state: ManageHabitsUiState,
    onEvent: (ManageHabitsEvent) -> Unit,
    onOpenInNotion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A `LazyColumn` rather than a scrolling `Column`: this list's length is the user's Notion
    // schema, so nothing here bounds it (`CLAUDE.md` §6). The header is an item of its own and
    // scrolls with the list — there is no sub-nav to keep in view, unlike Week and Month.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                Text(
                    text = stringResource(R.string.screen_title_habits),
                    style = MaterialTheme.typography.headlineSmall,
                    color = HabitsTheme.colors.text.primary,
                )
                if (state is ManageHabitsUiState.Content) {
                    WhereHabitsLiveCard(notionUrl = state.notionUrl, onOpenInNotion = onOpenInNotion)
                }
            }
        }

        // Loading draws the title and stops. It is a Room read, so it is over in milliseconds, and
        // a spinner that flashes on every visit is worse than a header that arrives alone.
        if (state !is ManageHabitsUiState.Content) return@LazyColumn

        if (state.habits.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.habits_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitsTheme.colors.text.secondary,
                )
            }
            return@LazyColumn
        }

        // Said once above the cards rather than as an eyebrow on each of them: five habits means
        // five repetitions of a sentence that is true of the screen, not of a habit.
        item(key = "colour-note") {
            Text(
                text = stringResource(R.string.habits_colour_body),
                style = MaterialTheme.typography.bodySmall,
                color = HabitsTheme.colors.text.secondary,
            )
        }

        items(items = state.habits, key = { habit -> habit.id.value }) { habit ->
            HabitRow(
                habit = habit,
                onColorPicked = { color ->
                    onEvent(ManageHabitsEvent.ColorPicked(habitId = habit.id, color = color))
                },
            )
        }
    }
}

/**
 * Where the habits come from, and the way there.
 *
 * The button is **disabled in place rather than hidden** when the app has not learned the database's
 * id yet (ADR-060). A control that materialises once a sync lands is one the user cannot plan
 * around; a visible one that says when it will work is.
 */
@Composable
private fun WhereHabitsLiveCard(
    notionUrl: String?,
    onOpenInNotion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HabitsCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.habits_body),
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.text.secondary,
        )
        SecondaryButton(
            text = stringResource(R.string.habits_open_in_notion),
            onClick = onOpenInNotion,
            enabled = notionUrl != null,
        )
        if (notionUrl == null) {
            Text(
                text = stringResource(R.string.habits_notion_pending),
                style = MaterialTheme.typography.bodySmall,
                color = HabitsTheme.colors.text.helper,
            )
        }
    }
}

/**
 * One habit: its icon and name, and its four colours.
 *
 * The swatches sit **under** the name rather than beside it. Four 48dp targets, a 40dp icon and a
 * name do not share one row on a narrow phone without the name truncating, and the name is the part
 * that identifies the row.
 */
@Composable
private fun HabitRow(
    habit: Habit,
    onColorPicked: (HabitColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = HabitColor.entries
    HabitsCard(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HabitIcon(emoji = habit.emoji, name = habit.name, habitColor = habit.color.resolve())
            Text(
                text = habit.name,
                style = MaterialTheme.typography.titleSmall,
                color = HabitsTheme.colors.text.primary,
            )
        }
        ColorSwatchRow(
            colors = palette.map { color -> color.resolve() },
            selectedIndex = palette.indexOf(habit.color),
            contentDescriptions = palette.map { color -> stringResource(color.labelRes()) },
            onSelect = { index -> onColorPicked(palette[index]) },
        )
    }
}

/**
 * The word for a colour, for a screen reader.
 *
 * A `when` over the enum rather than a map, so adding a colour to [HabitColor] fails to compile here
 * instead of announcing a swatch as nothing.
 */
private fun HabitColor.labelRes(): Int =
    when (this) {
        HabitColor.BLUE -> R.string.habits_colour_blue
        HabitColor.GREEN -> R.string.habits_colour_green
        HabitColor.ORANGE -> R.string.habits_colour_orange
        HabitColor.PURPLE -> R.string.habits_colour_purple
    }

@Preview(name = "Habits", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ManageHabitsPreview() {
    HabitsTheme {
        ManageHabitsContent(
            state =
                ManageHabitsUiState.Content(
                    habits = SampleHabits.habits(),
                    notionUrl = "https://www.notion.so/0123456789abcdef0123456789abcdef",
                ),
            onEvent = {},
            onOpenInNotion = {},
        )
    }
}

@Preview(name = "Nothing imported yet", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun ManageHabitsEmptyPreview() {
    HabitsTheme {
        ManageHabitsContent(
            state = ManageHabitsUiState.Content(habits = emptyList(), notionUrl = null),
            onEvent = {},
            onOpenInNotion = {},
        )
    }
}
