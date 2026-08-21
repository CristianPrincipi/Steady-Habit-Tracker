package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideIcon
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing

/**
 * The `‹ August 2026 ›` row above the Month and Week views (`docs/design-spec.md §2`, `§3`).
 *
 * The period label is passed in already formatted — the design system does no date maths and knows
 * no locale. Same for the two content descriptions: this component cannot know whether it is
 * stepping months or weeks.
 *
 * There is no "jump to today" affordance. That is the design's choice, not an omission.
 */
@Composable
fun NavArrows(
    label: String,
    previousContentDescription: String,
    nextContentDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronButton(
            pointsLeft = true,
            contentDescription = previousContentDescription,
            onClick = onPrevious,
        )
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = MaterialTheme.typography.titleSmall.fontWeight,
                ),
            color = HabitsTheme.colors.text.navLabel,
        )
        ChevronButton(
            pointsLeft = false,
            contentDescription = nextContentDescription,
            onClick = onNext,
        )
    }
}

/**
 * One 32dp arrow inside a 48dp target.
 *
 * The chevron is drawn rather than imported. It is the same two-segment polyline in Lucide and in
 * Material Symbols, so hand-drawing it settles nothing that ADR-024 left open, and it avoids a
 * drawable whose only job is two lines.
 */
@Composable
private fun ChevronButton(
    pointsLeft: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint = HabitsTheme.colors.text.secondary

    Box(
        modifier =
            Modifier
                .size(Sizing.minimumTouchTarget)
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                }.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).pressScale(interactionSource, ARROW_PRESSED_SCALE),
        contentAlignment = Alignment.Center,
    ) {
        // 18dp of glyph inside a 32dp button inside a 48dp target — docs/tokens.md §4.4 and §5.
        // Three sizes for one arrow looks fussy written down and is exactly what the design does:
        // the button is the visual weight, the glyph is smaller than it, and the target is bigger.
        LucideIcon(
            glyph = if (pointsLeft) Lucide.ChevronLeft else Lucide.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(Sizing.iconMedium),
            tint = tint,
        )
    }
}

/** `active:scale-90` on both arrows — `docs/tokens.md §6`. */
private const val ARROW_PRESSED_SCALE = 0.90f

@Preview(backgroundColor = 0xFF111113, showBackground = true)
@Composable
private fun NavArrowsPreview() {
    HabitsTheme {
        NavArrows(
            label = "August 2026",
            previousContentDescription = "Previous month",
            nextContentDescription = "Next month",
            onPrevious = {},
            onNext = {},
        )
    }
}
