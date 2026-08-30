package com.cristianprincipi.habits.core.designsystem.component

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideIcon
import com.cristianprincipi.habits.core.designsystem.icon.LucideStroke
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
 *   ring is drawn whether or not that day is complete — see [todayRing], which is what makes that
 *   true in pixels rather than only in code.
 * @param onToggle fired with the index of a day that can be checked off. **Days that cannot are
 *   disabled rather than silently inert** — see [isCheckable]. A tap on tomorrow does not reach
 *   here, does not reach the domain, and says nothing (`Plan.md` M6.7).
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
                    // ⚠ Disabled, not absent. `enabled = false` keeps the node a checkbox and marks
                    // it unavailable, so a screen reader still finds a day here and says it cannot
                    // be ticked. Dropping the modifier instead would leave a pill that a sighted
                    // user can see and a blind one cannot — and "disabled in place" is what this app
                    // does elsewhere (ADR-060).
                    enabled = status.isCheckable,
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
                        if (isToday) {
                            Modifier.border(Sizing.todayRing, todayRing(completed, habitColor, checkColor), shape)
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                LucideIcon(
                    glyph = Lucide.Check,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.iconSmall),
                    tint = checkColor,
                    strokeWidth = LucideStroke.CHECK,
                )
            }
        }
    }
}

/**
 * The colour of the ring that marks today — M6.11, and it is not one colour.
 *
 * ### ⚠ Drawn in the habit's colour, the ring disappears the moment the day is done
 *
 * `Modifier.border` draws **inside** the pill's bounds; a completed pill is *filled* with the habit
 * colour, so a habit-coloured ring on top of it is the same colour on the same colour and reads as
 * nothing at all. That is not an edge case — a habit done every day this week never shows its today
 * marker once, and the user watched exactly that on a device at M6.11 (four of five habits).
 *
 * The prototype does not have this problem and never had to solve it: it rings today with a
 * `box-shadow`, which sits **outside** the element, so it got a slightly larger pill where Compose
 * gives a slightly smaller inside edge. Reproducing that here would cost width the row does not
 * have — seven pills across a card already leaves each one 44.2dp (ADR-069), below the 48dp the
 * height reaches.
 *
 * **So the ring changes colour instead of position:** [completedColor] — the same token the tick
 * inside it uses, so the two read as one mark — on a completed pill, and [habitColor] on every
 * other, which is the design's own treatment and the case that always worked.
 */
private fun todayRing(
    completed: Boolean,
    habitColor: Color,
    completedColor: Color,
): Color = if (completed) completedColor else habitColor

/**
 * Whether a tap on this day can do anything — `Plan.md` M6.7.
 *
 * ⚠ **This is an affordance, not the rule.** The rule lives in `ToggleCompletion`, which refuses a
 * date that has not happened yet and is unit tested for it; `WeekViewModelTest` separately pins that
 * the ViewModel does not route around it. This stops the tap **reaching** any of that, so the two
 * are belt and braces rather than one guard in two places. **If they ever disagree, the domain is
 * right and this is the bug.**
 *
 * The design already says all of this without a word: `pillFill` dims [DayStatus.FUTURE] and
 * [DayStatus.BEFORE_START] to `DayOpacity.WEEK_FUTURE`, the prototype's 30%. What was missing was
 * that the *dimmed* pill still behaved like a live checkbox.
 *
 * - [DayStatus.FUTURE] — tomorrow has not happened. `ToggleCompletion` would refuse it anyway.
 * - [DayStatus.BEFORE_START] — the habit did not exist yet, so there is no day to write to.
 * - [DayStatus.PADDING] — not a day at all. It cannot occur in a week (`weekWindow` produces none),
 *   and `WeekContent.toggleAt` already returns `null` for one; this makes the pill match.
 *
 * **Past days are deliberately absent from that list.** They stay freely editable: Notion allows it,
 * and catching up on a day you missed is most of what a week view is for (`Plan.md` M6.7).
 */
private val DayStatus.isCheckable: Boolean
    get() =
        when (this) {
            DayStatus.COMPLETED, DayStatus.INCOMPLETE -> true
            DayStatus.FUTURE, DayStatus.BEFORE_START, DayStatus.PADDING -> false
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
