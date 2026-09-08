package com.cristianprincipi.habits.feature.managehabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.ColorSwatchRow
import com.cristianprincipi.habits.core.designsystem.component.HabitIcon
import com.cristianprincipi.habits.core.designsystem.component.HabitsCard
import com.cristianprincipi.habits.core.designsystem.component.SecondaryButton
import com.cristianprincipi.habits.core.designsystem.component.pressScale
import com.cristianprincipi.habits.core.designsystem.component.readingWidth
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideIcon
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.usecase.MoveDirection

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
    onEditMapping: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A `LazyColumn` rather than a scrolling `Column`: this list's length is the user's Notion
    // schema, so nothing here bounds it (`CLAUDE.md` §6). The header is an item of its own and
    // scrolls with the list — there is no sub-nav to keep in view, unlike Week and Month.
    LazyColumn(
        modifier = modifier.readingWidth().fillMaxSize(),
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
                    WhereHabitsLiveCard(
                        notionUrl = state.notionUrl,
                        onOpenInNotion = onOpenInNotion,
                        onEditMapping = onEditMapping,
                    )
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

        // ⭐ M12.5 — see `Motion`'s closing note for why this is a platform spring and not a token.
        itemsIndexed(items = state.habits, key = { _, habit -> habit.id.value }) { index, habit ->
            HabitRow(
                habit = habit,
                // ⚠ Disabled at the ends rather than absent, which is this project's standing
                // preference (ADR-060) and matters more here than usual: a control that appears
                // and vanishes as a row moves would flicker under the very finger moving it.
                canMoveUp = index > 0,
                canMoveDown = index < state.habits.lastIndex,
                onColorPicked = { color ->
                    onEvent(ManageHabitsEvent.ColorPicked(habitId = habit.id, color = color))
                },
                onMove = { direction ->
                    onEvent(ManageHabitsEvent.Moved(habitId = habit.id, direction = direction))
                },
                modifier = Modifier.animateItem(),
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
    onEditMapping: () -> Unit,
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
        // ⭐ M7.8's sentence and its route, which ADR-079 said this screen owed and could not yet
        // say. It is below the Notion link on purpose: the order is what habits are, then where to
        // change them in Notion, then what to do here once you have.
        Text(
            text = stringResource(R.string.habits_mapping_body),
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.text.secondary,
        )
        SecondaryButton(
            text = stringResource(R.string.mapping_edit_action),
            onClick = onEditMapping,
        )
    }
}

/**
 * One habit: its icon and name, its two move buttons, and its five colours.
 *
 * The swatches sit **under** the name rather than beside it. Five 48dp targets, a 40dp icon and a
 * name do not share one row on a narrow phone without the name truncating, and the name is the part
 * that identifies the row.
 *
 * ⭐ **The move buttons do share that top row**, because there are only two of them and they belong
 * beside the thing they move rather than under it. The name takes `weight(1f)` so they sit hard
 * right, where a thumb reaches them.
 */
@Composable
private fun HabitRow(
    habit: Habit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onColorPicked: (HabitColor) -> Unit,
    onMove: (MoveDirection) -> Unit,
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
                modifier = Modifier.weight(1f),
            )
            MoveButton(
                direction = MoveDirection.Up,
                habitName = habit.name,
                enabled = canMoveUp,
                onClick = { onMove(MoveDirection.Up) },
            )
            MoveButton(
                direction = MoveDirection.Down,
                habitName = habit.name,
                enabled = canMoveDown,
                onClick = { onMove(MoveDirection.Down) },
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
 * One move arrow — M15.5.
 *
 * ### ⭐⭐ The glyph is `ChevronLeft`/`ChevronRight` **rotated**, not a new one
 *
 * There is no `chevron-up` or `chevron-down` among the app's thirteen Lucide glyphs, and
 * `LucideGlyphs` is transcribed verbatim from the upstream SVGs — *"nothing was redrawn by hand"* —
 * with ADR-024 settling the family on purpose. Rotating the horizontal chevron a quarter turn gives
 * the vertical one **from the same path data**, so this needs no fourteenth glyph, no invented
 * coordinates and no decision that belongs to the user. `ChevronLeft` turned 90° clockwise points
 * up; `ChevronRight` turned the same way points down.
 *
 * ### The three sizes are `ChevronButton`'s, deliberately
 *
 * 18dp of glyph inside a 48dp target, matching the Month and Week nav arrows: the same gesture
 * ("move by one") should not have two different targets in one app.
 *
 * ⚠⚠ **The label is a `contentDescription`, not `clickable`'s `onClickLabel`, and the difference
 * is whether the button has a name at all.** `onClickLabel` names the *action* — a screen reader
 * says "button, double tap to move Read up", so the node itself is announced as an anonymous
 * button, and touch exploration lands on it with nothing to say. A `contentDescription` names the
 * node: "Move Read up, button", and "disabled" after it when it is. This is `ChevronButton`'s own
 * pattern in `NavArrows`, and M13.1 found the same defect on `SetupTextField` — twice is enough to
 * treat it as the house rule.
 *
 * ⚠ **Disabled rather than hidden at the ends**, and `clickable(enabled = false)` is what carries
 * that into the semantics, which is the reason not to hand-roll them.
 */
@Composable
private fun MoveButton(
    direction: MoveDirection,
    habitName: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label =
        when (direction) {
            MoveDirection.Up -> stringResource(R.string.habits_move_up, habitName)
            MoveDirection.Down -> stringResource(R.string.habits_move_down, habitName)
        }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .size(Sizing.minimumTouchTarget)
                .semantics {
                    role = Role.Button
                    this.contentDescription = label
                    if (!enabled) disabled()
                }.clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).pressScale(interactionSource, ARROW_PRESSED_SCALE),
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(
            glyph = if (direction == MoveDirection.Up) Lucide.ChevronLeft else Lucide.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(Sizing.iconMedium).rotate(QUARTER_TURN),
            tint =
                if (enabled) {
                    HabitsTheme.colors.text.secondary
                } else {
                    HabitsTheme.colors.text.disabled
                },
        )
    }
}

/** Clockwise, which turns a left chevron into an up one and a right chevron into a down one. */
private const val QUARTER_TURN = 90f

/**
 * `active:scale-90`, the same press feedback the Month and Week nav arrows use.
 *
 * ⚠ **It is not decoration here, it is the replacement for the ripple.** `indication = null` on
 * the `clickable` above follows `ChevronButton`, and a bare 48dp box with no ripple and no scale
 * would give a tap no acknowledgement at all — on a control whose whole job is to be tapped
 * repeatedly.
 */
private const val ARROW_PRESSED_SCALE = 0.90f

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
        HabitColor.RED -> R.string.habits_colour_red
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
            onEditMapping = {},
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
            onEditMapping = {},
        )
    }
}
