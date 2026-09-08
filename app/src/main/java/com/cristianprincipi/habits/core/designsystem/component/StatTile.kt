package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideGlyph
import com.cristianprincipi.habits.core.designsystem.icon.LucideIcon
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * One of the four numbers on the Stats screen (`docs/design-spec.md §5.3`).
 *
 * A 28dp chip holding a 13dp icon tinted with the habit colour, a label, then the value. Laid out in
 * a 2×2 grid by the caller, which is what makes the tile a fixed-size box: whatever the digits do,
 * the tile does not move.
 *
 * ⚠ **The digits shift as the value changes, and that is the design.** ADR-021 kept a stable-width
 * alternative alive for eight milestones so the choice could be made by eye; the user chose DM Sans
 * at M9.8 and the alternative is gone. `HabitsTextStyles.statValue` asks for `tnum` anyway, inertly,
 * because DM Sans 4.004 does not have it — see that file.
 *
 * @param icon supplied by the caller so the icon set can change without touching this signature —
 *   the four are Lucide's `Hash`, `Percent`, `Flame` and `Trophy` (ADR-024). The slot is expected
 *   to draw at [Sizing.iconSmall] in [habitColor]; [StatTileIcon] does exactly that.
 *
 *   ⚠⚠ **`null`, not an empty lambda, for a tile with no icon — M15.4.** The default used to be
 *   `{}`, and a caller that took it got the 28dp tinted chip **with nothing in it**: on screen a
 *   small coloured square that reads as an icon which failed to load. Found by rendering the
 *   measure tiles, which have no apt glyph among the thirteen. A nullable slot lets the chip be
 *   omitted rather than drawn empty, and makes "this tile has no icon" a thing the type can say.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    habitColor: Color,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    // ⚠⚠ **Merged (M13.2).** Label and value are two `Text`s in a `Column`, and a `Column` merges
    // nothing — so a screen reader stopped on "Current streak", then on "9", and a user who explored
    // by touch could land on the second without ever hearing the first. Four tiles were eight stops,
    // half of them bare numbers. Merged, a tile is one stop that says both, in the order it draws.
    HabitsCard(
        modifier = modifier.semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(Spacing.weekPillGap),
    ) {
        // The chip is the icon's background, so no icon means no chip. Drawing it empty is
        // what M15.4 caught on the emulator, and it looked like a bug rather than like a choice.
        if (icon != null) {
            Box(
                modifier =
                    Modifier
                        .size(Sizing.statTileIcon)
                        .background(
                            color = habitColor.copy(alpha = HabitsTheme.colors.tint.yearDotIncomplete),
                            shape = MaterialTheme.shapes.medium,
                        ),
                contentAlignment = Alignment.Center,
                content = { icon() },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HabitsTheme.colors.text.tertiary,
        )
        Text(
            text = value,
            style = HabitsTheme.textStyles.statValue,
            color = HabitsTheme.colors.text.primary,
        )
    }
}

/** The icon slot filled the way every stat tile fills it: one glyph, 13dp, in the habit's colour. */
@Composable
fun StatTileIcon(
    glyph: LucideGlyph,
    habitColor: Color,
) {
    LucideIcon(
        glyph = glyph,
        contentDescription = null,
        modifier = Modifier.size(Sizing.iconSmall),
        tint = habitColor,
    )
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun StatTilePreview() {
    HabitsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                StatTile(
                    label = "Completed days",
                    icon = { StatTileIcon(Lucide.Hash, HabitsTheme.colors.habit.purple) },
                    value = "111",
                    habitColor = HabitsTheme.colors.habit.purple,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Completion rate",
                    icon = { StatTileIcon(Lucide.Percent, HabitsTheme.colors.habit.purple) },
                    value = "84%",
                    habitColor = HabitsTheme.colors.habit.purple,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                StatTile(
                    label = "Current streak",
                    icon = { StatTileIcon(Lucide.Flame, HabitsTheme.colors.habit.purple) },
                    value = "111",
                    habitColor = HabitsTheme.colors.habit.purple,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Best streak",
                    icon = { StatTileIcon(Lucide.Trophy, HabitsTheme.colors.habit.purple) },
                    value = "84%",
                    habitColor = HabitsTheme.colors.habit.purple,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
