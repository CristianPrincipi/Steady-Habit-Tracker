package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideIcon
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Motion
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/*
 * The app's buttons.
 *
 * In the design system rather than in a feature because the 14dp radius correction is the sort of
 * thing that gets forgotten on the fourth screen: Material routes a `Button` to its own shape tier,
 * which is NOT the design's radius for buttons (ADR-022). Writing it once means it cannot drift.
 *
 * UNDESIGNED beyond the primary button. The prototype has exactly one — the blue full-width Connect
 * button (`docs/design-spec.md` §6.5) — so [PrimaryButton] is ported and the other two are built
 * from the same vocabulary. `Plan.md` M2.8 applies.
 */

/**
 * The one primary action on a screen: full width, the design's blue, 14dp radius.
 *
 * @param isBusy swaps the label for a spinner **inside the button**, rather than putting a dimmer
 *   over the screen. That is the difference between "this is working" and a modal that blocks
 *   everything, which `CLAUDE.md` §3.5 rules out for anything sync-related.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBusy: Boolean = false,
) {
    val container = fadeOnDisable(MaterialTheme.colorScheme.primary, enabled, "primaryContainer")
    val content = fadeOnDisable(HabitsTheme.colors.text.primary, enabled, "primaryContent")
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        // Both slots take the same animated colour. Material picks one of the pair by `enabled` and
        // snaps between them (`Button.kt:1085` — it does not animate), so handing it the *already
        // faded* value on both sides is what turns its cut into the design's transition.
        colors =
            ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = container,
                disabledContentColor = content,
            ),
    ) {
        ButtonBody(text = text, isBusy = isBusy, spinnerColor = HabitsTheme.colors.text.primary)
    }
}

/**
 * A secondary action: the same shape, an outline instead of a fill.
 *
 * For "try again" beside a failure — something worth offering and not worth shouting about.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBusy: Boolean = false,
) {
    // Not [fadeOnDisable]: this one's disabled colour is a *token* rather than the enabled colour at
    // 35%, so the two ends are named separately and only the crossing between them is shared.
    val content by
        animateColorAsState(
            targetValue =
                if (enabled) HabitsTheme.colors.text.primary else HabitsTheme.colors.text.disabled,
            animationSpec = Motion.colorStateChange,
            label = "secondaryContent",
        )
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = content,
                disabledContentColor = content,
            ),
    ) {
        ButtonBody(text = text, isBusy = isBusy, spinnerColor = HabitsTheme.colors.text.primary)
    }
}

/**
 * A row that goes somewhere — **the Settings hub's three destinations**, and nothing else so far.
 *
 * ### ⭐⭐ Why this exists rather than another [SecondaryButton] — M13.10, the user's ruling
 *
 * Those three rows *were* `SecondaryButton`s, because `docs/design-spec.md` §6 designs no rows at
 * all and M2's buttons were the nearest thing that existed. The QA sweep put them on a device and
 * the user ruled: **a centred label in a full-width pill makes three destinations look like three
 * equal-weight actions**, when what they are is navigation to three other screens. A chevron says
 * "this goes somewhere" without spending a word of copy on it, and left-aligning the label is what
 * makes a list read as a list.
 *
 * ⚠ It is still an `OutlinedButton` underneath, and deliberately: the border, the shape, the ripple
 * and the 48dp touch target are already right, and the change the user asked for is about
 * *arrangement*, not about inventing a control. Only the content slot differs.
 *
 * ⚠ **The chevron takes `contentDescription = null` on purpose.** `OutlinedButton` merges its
 * descendants, so the row already reaches a screen reader as its label; a described chevron would
 * add "chevron right" to the end of every one of them — the class of noise M13.2 removed 35 of.
 */
@Composable
fun NavigationRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HabitsTheme.colors.text.primary),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        LucideIcon(
            glyph = Lucide.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(Sizing.iconMedium),
            tint = HabitsTheme.colors.text.tertiary,
        )
    }
}

/**
 * An action that takes something away — **disconnect**, and nothing else so far.
 *
 * Outlined in the error colour rather than filled with it: a filled red button is the loudest thing
 * on a screen, and this one sits on a settings page next to a status line, not at the end of a
 * confirmation the user has already thought about. The confirmation is where the weight belongs.
 */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val content = fadeOnDisable(MaterialTheme.colorScheme.error, enabled, "destructiveContent")
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = content,
                disabledContentColor = content,
            ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * [full] when the button is live, [DISABLED_ALPHA] of it when it is not — crossed, not cut.
 *
 * ⭐ M12.5, and the third of the design's three `transition-all`s: the Connect button is
 * `transition-all disabled:opacity-35` (`App.tsx:518`). It is the one the user actually watches —
 * pasting a token is a screen where the only thing that changes is this button waking up, and a
 * fade is what says *that paste was the thing that mattered*.
 *
 * ⚠ **It matters most where nobody is looking at the button.** ADR-060's rule is that a control the
 * app cannot honour is disabled **in place** rather than hidden, so "Sync now" dims while a sync
 * runs and lights again when it lands — a change the user is not waiting for, arriving on a screen
 * they are reading. Cut, that flickers. At 150ms it settles.
 */
@Composable
private fun fadeOnDisable(
    full: Color,
    enabled: Boolean,
    label: String,
): Color {
    val color by
        animateColorAsState(
            targetValue = if (enabled) full else full.copy(alpha = DISABLED_ALPHA),
            animationSpec = Motion.colorStateChange,
            label = label,
        )
    return color
}

@Composable
private fun ButtonBody(
    text: String,
    isBusy: Boolean,
    spinnerColor: Color,
) {
    if (isBusy) {
        CircularProgressIndicator(
            modifier = Modifier.size(Sizing.iconMedium),
            color = spinnerColor,
            strokeWidth = Sizing.todayRing,
        )
    } else {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

private const val DISABLED_ALPHA = 0.35f

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun ButtonsPreview() {
    HabitsTheme {
        Column(
            modifier = Modifier.padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            PrimaryButton(text = "Continue", onClick = {})
            PrimaryButton(text = "Continue", onClick = {}, enabled = false)
            PrimaryButton(text = "Continue", onClick = {}, isBusy = true)
            SecondaryButton(text = "Try again", onClick = {})
            DestructiveButton(text = "Disconnect from Notion", onClick = {})
        }
    }
}
