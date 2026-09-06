package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.datetime.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideIcon
import com.cristianprincipi.habits.core.designsystem.icon.LucideStroke
import com.cristianprincipi.habits.core.designsystem.theme.DayOpacity
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Motion
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Seven equal columns with six gaps between them — the same arithmetic both rows below lay
        // out by, written once here because the heading needs to know it before it draws.
        val columnWidth = (maxWidth - Spacing.weekPillGap * (DAYS_PER_WEEK - 1)) / DAYS_PER_WEEK

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.weekPillGap)) {
            // ⚠⚠ M13.2 — **silent, and it is the largest single win in the TalkBack pass.**
            //
            // These letters are a column heading: they are read *down* the pill under each one,
            // which is a move only a sighted user makes. A screen reader goes along the row, and
            // every pill it reaches already announces the weekday in full — "Read, Monday, August
            // 17, 2026". Left audible, the row costs seven stops per card, and five cards is **35
            // stops that add nothing** to what the next seven say properly.
            //
            // ⭐ Cleared rather than merged away, and the order matters: [WeekHabitCard]'s header is
            // a merging boundary, and an uncleared row would be swallowed into some node rather than
            // dropped. `clearAndSetSemantics` is also what ADR-089 does to the dot grids, for the
            // same reason — a thing drawn for the eye is not automatically a thing worth saying.
            //
            // ⚠⚠ M13.3 — **and one size for the row, because it shrinks rather than wraps.** At 200%
            // font on a 320dp window each of these columns is ~31dp and "Mon" is wider than that, so
            // Compose broke the word: `Mo` over `n`, four of the seven. An ellipsis would say `M…`
            // and a clip would say `Mo`; a smaller word is still a word.
            //
            // ⚠ **`fittedFontSize` rather than `BasicText(autoSize = …)`, and the difference is
            // visible.** Compose's own auto-sizing is per-`Text`: it shrank `Mon` and `Wed` and left
            // the other five at full size, and a heading row at two sizes reads as a rendering fault
            // rather than as a design.
            //
            // ⚠ **Shrinking is legitimate here only because the row is decorative** — the semantics
            // above are cleared, so declining part of the font scale costs a screen reader nothing.
            // The stat tiles look like the same problem and are not: they are content, and M13.3
            // gives them room instead.
            val labelStyle = HabitsTheme.textStyles.weekdayInitial
            val labelSize = fittedFontSize(texts = dayLabels, style = labelStyle, available = columnWidth)

            Row(
                modifier = Modifier.clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(Spacing.weekPillGap),
            ) {
                dayLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = labelStyle.copy(fontSize = labelSize),
                        color = HabitsTheme.colors.text.eyebrow,
                        maxLines = 1,
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
    val haptics = LocalHapticFeedback.current
    val completed = status == DayStatus.COMPLETED
    val shape = MaterialTheme.shapes.large
    val checkColor = HabitsTheme.colors.text.primary

    // ⭐ M12.5, and the check-off animation is a **port** rather than an invention: the prototype's
    // day pill carries `transition-all` (`App.tsx:222`), so its fill and its 30% future dim cross
    // over 150ms instead of cutting. Ours snapped.
    //
    // ⚠ **One `animateColorAsState` covers both** because `pillFill` folds the design's `opacity`
    // into the fill's alpha — a colour interpolation carries the alpha with it, so a day going
    // FUTURE → INCOMPLETE brightens on the same curve a day going INCOMPLETE → COMPLETED fills on.
    // Two animations would be two chances for them to disagree by a frame.
    val fill by animateColorAsState(
        targetValue = habitColor.pillFill(status, HabitsTheme.colors.tint.weekPillIncomplete),
        animationSpec = Motion.colorStateChange,
        label = "pillFill",
    )
    // The ring *changes colour* as the day completes rather than appearing or leaving — see
    // [todayRing] — so it is the one thing on this pill that a transition was always going to suit.
    // Computed whether or not this is today: keeping the call unconditional keeps the composition
    // shape the same for all seven pills, and the border below is what decides if it is drawn.
    // The neutral hairline on an unticked day that is not today — see the `when` below.
    val outline =
        HabitsTheme.colors.text.primary
            .copy(alpha = HabitsTheme.colors.tint.weekPillOutline)
    val ring by animateColorAsState(
        targetValue = todayRing(completed, habitColor, checkColor),
        animationSpec = Motion.colorStateChange,
        label = "todayRing",
    )

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
                    // ⭐ M12.6, and the only haptic in the app. The check-off is the one gesture
                    // `CLAUDE.md` §1 calls the core loop, and it is the one place where the finger
                    // is somewhere the eye may not be — seven 37dp-wide pills across five stacked
                    // cards. A pill that flips silently is a tap you have to look up to confirm.
                    //
                    // **`ToggleOn`/`ToggleOff` rather than one buzz for both**, because the two
                    // outcomes are not the same event: one records a day, the other takes it back.
                    //
                    // ⚠ **These are safe below API 34 despite the platform constants arriving
                    // there** — checked in androidx.core's source rather than assumed.
                    // `HapticFeedbackConstantsCompat.getFeedbackConstantOrFallback` maps `TOGGLE_ON`
                    // to `CONTEXT_CLICK` and `TOGGLE_OFF` to `CLOCK_TICK` below 34, and Compose calls
                    // it through `ViewCompat.performHapticFeedback`. Both fallbacks predate
                    // `minSdk 26`, and the asymmetry survives: on stays firmer than off.
                    //
                    // The system setting is honoured by `performHapticFeedback` itself, so a user who
                    // has turned haptics off is not overridden here.
                    onValueChange = { nowCompleted ->
                        haptics.performHapticFeedback(
                            if (nowCompleted) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onToggle()
                    },
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
                    .background(color = fill, shape = shape)
                    .then(
                        // The design rings today whether or not it is complete, so this is not an
                        // `else` on the fill above.
                        //
                        // ⚠⚠ M13.4 — **and an unticked day is now outlined too, wherever it falls in
                        // the week.** At `weekPillIncomplete` an empty pill is **1.21:1** against its
                        // card: WCAG 1.4.11 asks for 3:1 on the information that identifies a control
                        // and its state, and at that ratio a low-vision user cannot see there is a
                        // checkbox there at all. ⚠ **Raising the fill instead does not work** — 3:1
                        // needs alpha 0.79 for purple, which draws an unticked day at nearly the
                        // ticked colour and destroys the state distinction the criterion exists for.
                        //
                        // ⭐ **Today still wins the ring**, in the habit colour at full strength, so
                        // the two never compete: today is coloured, every other unticked day is a
                        // neutral hairline, and a ticked day has no outline unless it is today.
                        when {
                            isToday -> Modifier.border(Sizing.todayRing, ring, shape)
                            !completed -> Modifier.border(Sizing.todayRing, outline, shape)
                            else -> Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            // ⚠ **The tick pops; only the fill transitions** — and that is the faithful port, not an
            // oversight. `transition-all` sits on the pill, and CSS cannot transition a child being
            // inserted, so the prototype's `{done && <Check/>}` appears at once over a fill that is
            // still crossing. Fading it in would be a nicer animation than the design has.
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
