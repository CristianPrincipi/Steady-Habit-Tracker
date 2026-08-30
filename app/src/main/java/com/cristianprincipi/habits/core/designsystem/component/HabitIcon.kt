package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.HabitsCorners
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * A habit's emoji on a circle tinted with the habit's own colour.
 *
 * The tint is the habit colour at 15.7% alpha (`docs/tokens.md §1.4`) — the strongest step on the
 * ramp, because this is the only place the design fills a shape rather than a dot.
 *
 * The emoji is decorative: the habit's name always sits beside it, so announcing the emoji as well
 * would make a screen reader read every row twice. Semantics are cleared rather than left to
 * default, which is why there is no `contentDescription` parameter.
 */
@Composable
fun HabitIcon(
    emoji: String,
    name: String,
    habitColor: Color,
    modifier: Modifier = Modifier,
    size: HabitIconSize = HabitIconSize.LARGE,
) {
    val diameter = if (size == HabitIconSize.SMALL) Sizing.habitIconSmall else Sizing.habitIconLarge
    val textStyle =
        if (size == HabitIconSize.SMALL) {
            MaterialTheme.typography.titleMedium
        } else {
            MaterialTheme.typography.titleLarge
        }

    Box(
        modifier =
            modifier
                .size(diameter)
                .background(
                    color = habitColor.copy(alpha = HabitsTheme.colors.tint.iconCircle),
                    shape = HabitsCorners.pill,
                ).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // ⚠ The glyph, not the emoji: a habit whose Notion property carries no emoji draws its
        // initial instead of an empty circle (M7.2, the user's call). `habitGlyph` owns that rule.
        // The colour is set only for the fallback — an emoji carries its own and `Color.Unspecified`
        // leaves it alone, where tinting would flatten it to a silhouette.
        val glyph = habitGlyph(emoji, name)
        Text(
            text = glyph,
            style = textStyle,
            color = if (glyph == emoji) Color.Unspecified else habitColor,
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true)
@Composable
private fun HabitIconPreview() {
    HabitsTheme {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            horizontalArrangement =
                androidx.compose.foundation.layout.Arrangement
                    .spacedBy(Spacing.chipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HabitIcon(
                emoji = "🎸",
                name = "Learn Guitar",
                habitColor = HabitsTheme.colors.habit.blue,
                size = HabitIconSize.SMALL,
            )
            HabitIcon(emoji = "💧", name = "Drink water", habitColor = HabitsTheme.colors.habit.green)
            HabitIcon(emoji = "🧘", name = "Morning Yoga", habitColor = HabitsTheme.colors.habit.orange)
            // No emoji — the case the user's own database does not contain today (M7.2).
            HabitIcon(emoji = "", name = "Meditate", habitColor = HabitsTheme.colors.habit.purple)
        }
    }
}
