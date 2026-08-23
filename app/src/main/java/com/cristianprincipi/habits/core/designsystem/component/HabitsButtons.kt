package com.cristianprincipi.habits.core.designsystem.component

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
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
    val dimmedContent =
        HabitsTheme.colors.text.primary
            .copy(alpha = DISABLED_ALPHA)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = HabitsTheme.colors.text.primary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = DISABLED_ALPHA),
                disabledContentColor = dimmedContent,
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = HabitsTheme.colors.text.primary,
                disabledContentColor = HabitsTheme.colors.text.tertiary,
            ),
    ) {
        ButtonBody(text = text, isBusy = isBusy, spinnerColor = HabitsTheme.colors.text.primary)
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
                disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = DISABLED_ALPHA),
            ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
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
