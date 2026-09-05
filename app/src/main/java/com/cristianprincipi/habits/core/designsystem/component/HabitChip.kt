package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.HabitsCorners
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Motion
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * A habit selector pill — the row across the top of the Stats screen (`docs/design-spec.md §5.1`).
 *
 * Selected is a solid fill of the habit colour with white text; unselected is the same colour at
 * 12.5% alpha with the text in the habit colour. Reads `{emoji} {name}`.
 *
 * `selectable` rather than `clickable`, so a screen reader announces the chip as one of a set and
 * says which is chosen. The row that hosts these should carry `Modifier.selectableGroup()`.
 */
@Composable
fun HabitChip(
    emoji: String,
    name: String,
    habitColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    // ⭐ M12.5. `transition-all` on the prototype's chip (`App.tsx:358`), and here it is doing more
    // work than it does on a week pill: selecting a chip **reverses fill and ink at once** — the
    // habit colour moves from the text to the background while white moves the other way. Snapped,
    // that is two things swapping in one frame; crossed over 150ms, it reads as one exchange.
    //
    // ⚠ And the selection is the whole Stats screen's subject, so this is also the moment the tiles,
    // the year grid and the chart below all change colour. The chip is what makes that feel caused.
    val fill by animateColorAsState(
        targetValue =
            if (selected) {
                habitColor
            } else {
                habitColor.copy(alpha = HabitsTheme.colors.tint.weekPillIncomplete)
            },
        animationSpec = Motion.colorStateChange,
        label = "chipFill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) HabitsTheme.colors.text.primary else habitColor,
        animationSpec = Motion.colorStateChange,
        label = "chipInk",
    )

    Row(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .pressScale(interactionSource, CHIP_PRESSED_SCALE)
                .background(color = fill, shape = HabitsCorners.pill)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = Spacing.cardGap, vertical = Spacing.weekPillGap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.weekPillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = habitLabel(emoji, name),
            // Selected reverses the fill and the ink: white on the habit colour, versus the habit
            // colour on a tint of itself.
            color = ink,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `active:scale-95` — habit chips press a little less deeply than pills do (`docs/tokens.md §6`). */
private const val CHIP_PRESSED_SCALE = 0.95f

@Preview(backgroundColor = 0xFF111113, showBackground = true)
@Composable
private fun HabitChipPreview() {
    HabitsTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            HabitChip(
                emoji = "🎸",
                name = "Learn Guitar",
                habitColor = HabitsTheme.colors.habit.blue,
                selected = true,
                onClick = {},
            )
            HabitChip(
                emoji = "💧",
                name = "Drink water",
                habitColor = HabitsTheme.colors.habit.green,
                selected = false,
                onClick = {},
            )
        }
    }
}
