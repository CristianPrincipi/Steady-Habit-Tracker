package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
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
        // The drawn box stays 32dp — docs/tokens.md §4.4 keeps the visual size and grows only the
        // target around it.
        Canvas(modifier = Modifier.size(Sizing.navArrowButton)) {
            chevron(pointsLeft = pointsLeft, color = tint, strokeWidth = CHEVRON_STROKE.toPx())
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.chevron(
    pointsLeft: Boolean,
    color: Color,
    strokeWidth: Float,
) {
    val centreX = size.width / 2f
    val centreY = size.height / 2f
    val reach = size.minDimension * CHEVRON_REACH
    val tipX = if (pointsLeft) centreX - reach / 2f else centreX + reach / 2f
    val backX = if (pointsLeft) centreX + reach / 2f else centreX - reach / 2f

    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path =
        androidx.compose.ui.graphics.Path().apply {
            moveTo(backX, centreY - reach)
            lineTo(tipX, centreY)
            lineTo(backX, centreY + reach)
        }
    drawPath(path = path, color = color, style = stroke)
}

/** `active:scale-90` on both arrows — `docs/tokens.md §6`. */
private const val ARROW_PRESSED_SCALE = 0.90f

/** Lucide draws its chevrons at stroke 2 on a 24px grid; scaled to an 18px glyph that is 1.5. */
private val CHEVRON_STROKE = Sizing.todayRing

/** How far the chevron reaches from its centre, as a fraction of the 32dp box. */
private const val CHEVRON_REACH = 0.16f

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
