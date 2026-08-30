package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideIcon
import com.cristianprincipi.habits.core.designsystem.icon.LucideStroke
import com.cristianprincipi.habits.core.designsystem.theme.HabitsCorners
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * Pick one colour out of a fixed palette — the only edit v1 can make to a habit (ADR-010).
 *
 * ⚠ **Undesigned.** The prototype has no colour picker and no click handler anywhere
 * (`docs/design-spec.md` §0), so every choice below comes from M2's design system rather than from
 * a frame. It is deliberately the plainest thing that works: a row of the actual colours, with the
 * chosen one carrying a check.
 *
 * ### Why the check, and not a ring
 *
 * A ring is the obvious marker and it is the one that failed. `Modifier.border` draws **inside** the
 * bounds, so a ring around a filled circle is the habit's colour drawn on the habit's colour —
 * exactly the defect M6.11 found on the Week view's today marker, where four of five habits showed
 * no marker at all. A glyph on top cannot fail that way, and it is the same signal a completed week
 * pill already uses.
 *
 * ### Every swatch is drawn at full strength
 *
 * Dimming the unselected ones would be the usual move and is wrong here: these *are* the colours,
 * and a user choosing between them is comparing them. The selection is carried by the check alone.
 *
 * @param colors the palette, in the order it should be shown. Four in v1
 *   (`HabitPalette.assignable`).
 * @param selectedIndex which one is set, or `-1` for none — a habit whose stored colour is not in
 *   [colors] shows no check rather than guessing at one.
 * @param contentDescriptions one per colour, since a swatch has no text of its own and "blue" is
 *   the only thing a screen reader can announce about it.
 * @param onSelect the index the user chose. Fires even for the already-selected swatch; a picker
 *   that swallowed that would be one a fat finger could get stuck in.
 */
@Composable
fun ColorSwatchRow(
    colors: List<Color>,
    selectedIndex: Int,
    contentDescriptions: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // `selectableGroup` is what makes a screen reader read this as "1 of 4" rather than as four
        // unrelated buttons that happen to sit in a row.
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { index, color ->
            Swatch(
                color = color,
                selected = index == selectedIndex,
                description = contentDescriptions.getOrElse(index) { "" },
                onSelect = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    description: String,
    onSelect: () -> Unit,
) {
    // The outer box is the 48dp target; the inner circle is the 28dp that gets drawn. Same shape as
    // `WeekPills`, and the same reason: the guideline is about fingers, not about pixels.
    Box(
        modifier =
            Modifier
                .size(Sizing.minimumTouchTarget)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onSelect,
                ).semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(Sizing.colorSwatch)
                    .background(color = color, shape = HabitsCorners.pill),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                LucideIcon(
                    glyph = Lucide.Check,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.iconSmall),
                    tint = HabitsTheme.colors.text.primary,
                    strokeWidth = LucideStroke.CHECK,
                )
            }
        }
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun ColorSwatchRowPreview() {
    HabitsTheme {
        ColorSwatchRow(
            colors = HabitsTheme.colors.habit.assignable,
            selectedIndex = 2,
            contentDescriptions = listOf("Blue", "Green", "Orange", "Purple"),
            onSelect = {},
        )
    }
}

@Preview(name = "Nothing selected", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun ColorSwatchRowUnsetPreview() {
    HabitsTheme {
        ColorSwatchRow(
            colors = HabitsTheme.colors.habit.assignable,
            selectedIndex = -1,
            contentDescriptions = listOf("Blue", "Green", "Orange", "Purple"),
            onSelect = {},
        )
    }
}
