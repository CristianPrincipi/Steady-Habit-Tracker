package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.DayOpacity
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * The seven days of a week, as pills — **and the only place in the app a habit gets checked off**
 * (ADR-009, `docs/design-spec.md §3`).
 *
 * That makes this the app's primary tap target, and the reason the touch handling here is fussier
 * than anywhere else: the drawn pill stays at the design's 36dp, while the target around it grows to
 * 48dp. Shrinking the design to satisfy the guideline would be the wrong fix; growing the target is
 * invisible and correct.
 *
 * @param days seven entries, Monday first. Anything other than seven is a caller bug, not a state
 *   this renders.
 * @param dayLabels the `Mon…Sun` initials, already localised — the design system does no date maths.
 * @param dayContentDescriptions what a screen reader announces per pill; the pill itself is a
 *   checkbox, so these should name the date, not the state.
 * @param todayIndex which pill gets the ring, or `-1` for a week that does not contain today. The
 *   ring is drawn whether or not that day is complete.
 * @param onToggle fired with the index. Future days are still reported — whether tomorrow can be
 *   checked off is a domain rule (M6), not a drawing one.
 */
@Composable
fun WeekPills(
    days: List<DayStatus>,
    dayLabels: List<String>,
    dayContentDescriptions: List<String>,
    habitColor: Color,
    todayIndex: Int,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.weekPillGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.weekPillGap)) {
            dayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = HabitsTheme.textStyles.weekdayInitial,
                    color = HabitsTheme.colors.text.eyebrow,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.weekPillGap)) {
            days.forEachIndexed { index, status ->
                DayPill(
                    status = status,
                    habitColor = habitColor,
                    isToday = index == todayIndex,
                    contentDescription = dayContentDescriptions.getOrElse(index) { "" },
                    onToggle = { onToggle(index) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.DayPill(
    status: DayStatus,
    habitColor: Color,
    isToday: Boolean,
    contentDescription: String,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val completed = status == DayStatus.COMPLETED
    val shape = MaterialTheme.shapes.large
    val checkColor = HabitsTheme.colors.text.primary

    // The outer box is the 48dp target; the inner one is the 36dp the design draws. `heightIn`
    // rather than `height`, so the row can still grow if a font scale demands it.
    Box(
        modifier =
            Modifier
                .weight(1f)
                .heightIn(min = Sizing.minimumTouchTarget)
                .toggleable(
                    value = completed,
                    interactionSource = interactionSource,
                    indication = null,
                    onValueChange = { onToggle() },
                ).semantics {
                    this.contentDescription = contentDescription
                    toggleableState = if (completed) ToggleableState.On else ToggleableState.Off
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Sizing.weekPillHeight)
                    .pressScale(interactionSource, PILL_PRESSED_SCALE)
                    .background(
                        color = habitColor.pillFill(status, HabitsTheme.colors.tint.weekPillIncomplete),
                        shape = shape,
                    ).then(
                        // The design rings today whether or not it is complete, so this is not an
                        // `else` on the fill above.
                        if (isToday) Modifier.border(Sizing.todayRing, habitColor, shape) else Modifier,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                Canvas(modifier = Modifier.size(Sizing.checkGlyph)) { check(checkColor, Sizing.checkStroke.toPx()) }
            }
        }
    }
}

/**
 * Two segments, round-joined — Lucide's `Check` is the same polyline, so drawing it settles nothing
 * ADR-024 left open. It is drawn rather than imported because the design asks for stroke 3 at 13dp,
 * a ratio a static vector drawable reproduces badly at other densities.
 */
private fun DrawScope.check(
    color: Color,
    strokeWidth: Float,
) {
    val path =
        Path().apply {
            moveTo(size.width * CHECK_START_X, size.height * CHECK_START_Y)
            lineTo(size.width * CHECK_ELBOW_X, size.height * CHECK_ELBOW_Y)
            lineTo(size.width * CHECK_END_X, size.height * CHECK_END_Y)
        }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun Color.pillFill(
    status: DayStatus,
    incompleteTint: Float,
): Color =
    when (status) {
        DayStatus.COMPLETED -> this

        DayStatus.INCOMPLETE -> copy(alpha = incompleteTint)

        // A future day is the incomplete fill, dimmed — the design draws it as the same pill at 30%.
        DayStatus.FUTURE, DayStatus.BEFORE_START -> copy(alpha = incompleteTint * DayOpacity.WEEK_FUTURE)

        DayStatus.PADDING -> Color.Transparent
    }

/** `active:scale-90` — `docs/tokens.md §6`. */
private const val PILL_PRESSED_SCALE = 0.90f

private const val CHECK_START_X = 0.18f
private const val CHECK_START_Y = 0.52f
private const val CHECK_ELBOW_X = 0.42f
private const val CHECK_ELBOW_Y = 0.76f
private const val CHECK_END_X = 0.84f
private const val CHECK_END_Y = 0.26f

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun WeekPillsPreview() {
    HabitsTheme {
        WeekPills(
            days = previewWeek(),
            dayLabels = PREVIEW_DAY_LABELS,
            dayContentDescriptions = PREVIEW_DAY_LABELS,
            habitColor = HabitsTheme.colors.habit.green,
            todayIndex = 4,
            onToggle = {},
            modifier = Modifier.padding(Spacing.cardPadding),
        )
    }
}

internal val PREVIEW_DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

internal fun previewWeek(): List<DayStatus> =
    listOf(
        DayStatus.COMPLETED,
        DayStatus.COMPLETED,
        DayStatus.INCOMPLETE,
        DayStatus.COMPLETED,
        DayStatus.INCOMPLETE,
        DayStatus.FUTURE,
        DayStatus.FUTURE,
    )
