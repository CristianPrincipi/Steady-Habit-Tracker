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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
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
 * @param icon supplied by the caller so the icon set can change without touching this signature —
 *   M2.6 fills these with Lucide's `Hash`, `Percent`, `Flame` and `Trophy` (ADR-024). The slot is
 *   expected to draw at [Sizing.checkGlyph] in [habitColor].
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    habitColor: Color,
    modifier: Modifier = Modifier,
    numeralStyle: NumeralStyle = NumeralStyle.SANS,
    icon: @Composable () -> Unit = {},
) {
    HabitsCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.weekPillGap)) {
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HabitsTheme.colors.text.tertiary,
        )
        Text(
            text = value,
            style = numeralStyle.statValue(),
            color = HabitsTheme.colors.text.primary,
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun StatTilePreview() {
    HabitsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                StatTile(
                    label = "Completed days",
                    value = "111",
                    habitColor = HabitsTheme.colors.habit.purple,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Completion rate",
                    value = "84%",
                    habitColor = HabitsTheme.colors.habit.purple,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                StatTile(
                    label = "Current streak",
                    value = "111",
                    habitColor = HabitsTheme.colors.habit.purple,
                    numeralStyle = NumeralStyle.MONO,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Best streak",
                    value = "84%",
                    habitColor = HabitsTheme.colors.habit.purple,
                    numeralStyle = NumeralStyle.MONO,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
