package com.cristianprincipi.habits.feature.notionsetup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.domain.model.MappingIssue
import com.cristianprincipi.habits.domain.model.SyncFailure

/*
 * The pieces every step of the wizard is built from.
 *
 * All of it is UNDESIGNED — the prototype is a read-only visualiser with one non-functional
 * connection form (ADR-008, `docs/design-spec.md` §6), so there is no mock for a database picker, a
 * property list or an error banner. Everything here is assembled out of the M2 design system's own
 * vocabulary rather than invented: the card surface, the eyebrow, the 14dp button radius, the focus
 * colours the theme already carries for exactly this purpose.
 *
 * `Plan.md` M2.8 applies — this needs the user's eye before it is trusted.
 */

/** The step's heading and its one-line explanation. */
@Composable
fun SetupHeading(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = HabitsTheme.colors.text.primary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.text.secondary,
        )
    }
}

/**
 * A labelled text field in the design's own idiom: an uppercase eyebrow label, monospace content,
 * a hairline border that brightens on focus.
 *
 * `docs/tokens.md` gives the token and database fields DM Mono, and
 * [com.cristianprincipi.habits.core.designsystem.theme.HabitsTextStyles.mono] is that style. It
 * matters for more than looks: an `l`/`1` or `O`/`0` confusion in a pasted id is invisible in a
 * proportional face.
 *
 * @param isSecret hides the characters. ⚠ The token field is the reason this parameter exists —
 *   `CLAUDE.md` §3.6 treats the token as a password, and a plain field puts it on screen for a
 *   shoulder or a screenshot.
 */
@Composable
fun SetupTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    helper: String,
    modifier: Modifier = Modifier,
    isSecret: Boolean = false,
    errorText: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        Text(
            text = label,
            style = HabitsTheme.textStyles.eyebrow,
            color = HabitsTheme.colors.text.eyebrow,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = HabitsTheme.textStyles.mono,
            placeholder = {
                Text(
                    text = placeholder,
                    style = HabitsTheme.textStyles.mono,
                    color = HabitsTheme.colors.text.placeholder,
                )
            },
            singleLine = true,
            isError = errorText != null,
            visualTransformation =
                if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (isSecret) KeyboardType.Password else KeyboardType.Uri,
                    autoCorrectEnabled = false,
                ),
            // Material routes an OutlinedTextField to its own shape tier, which is not the design's
            // 14dp — the same correction ADR-022 records for Button.
            shape = MaterialTheme.shapes.large,
            colors =
                TextFieldDefaults.colors(
                    focusedTextColor = HabitsTheme.colors.text.primary,
                    unfocusedTextColor = HabitsTheme.colors.text.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = HabitsTheme.colors.focus.border,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
        )
        Text(
            text = errorText ?: helper,
            style = MaterialTheme.typography.bodySmall,
            color = if (errorText != null) MaterialTheme.colorScheme.error else HabitsTheme.colors.text.helper,
        )
    }
}

/**
 * The step's one primary action: full width, 14dp radius, and a spinner in place of its label while
 * a request is in flight.
 *
 * The spinner sits **inside the button** rather than over the screen, which is the difference
 * between "this is working" and a modal that blocks everything. `CLAUDE.md` §3.5 asks for the
 * latter never to happen to the sync loop; the same instinct applies here.
 */
@Composable
fun SetupPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBusy: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = ButtonDefaults.ContentPadding,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = HabitsTheme.colors.text.primary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = DISABLED_ALPHA),
                disabledContentColor =
                    HabitsTheme.colors.text.primary
                        .copy(alpha = DISABLED_ALPHA),
            ),
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizing.iconMedium),
                color = HabitsTheme.colors.text.primary,
                strokeWidth = Sizing.todayRing,
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * A failure, said as the thing to do about it.
 *
 * ⭐ The `when` below is the reason [SyncFailure] is a closed set: adding a way to fail stops this
 * compiling, so no failure can ship without a sentence. The warning palette rather than the error
 * one — the prototype's own banner treatment (`docs/design-spec.md` §6.2), and right for this
 * screen, where almost every case is something the user can go and fix.
 */
@Composable
fun SetupFailureBanner(
    failure: SyncFailure,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = HabitsTheme.colors.warning.copy(alpha = BANNER_FILL_ALPHA),
                    shape = MaterialTheme.shapes.large,
                ).border(
                    width = Sizing.hairlineBorder,
                    color = HabitsTheme.colors.warning.copy(alpha = BANNER_BORDER_ALPHA),
                    shape = MaterialTheme.shapes.large,
                ).padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        Text(
            text = stringResource(failure.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.warning,
        )
        if (failure is SyncFailure.MappingBroken) {
            failure.issues.forEach { issue ->
                Text(
                    text = issue.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabitsTheme.colors.text.secondary,
                )
            }
        }
    }
}

private fun SyncFailure.messageRes(): Int =
    when (this) {
        SyncFailure.NotConnected -> R.string.setup_failure_not_connected
        SyncFailure.TokenRejected -> R.string.setup_failure_token_rejected
        SyncFailure.MissingCapability -> R.string.setup_failure_missing_capability
        SyncFailure.DatabaseNotShared -> R.string.setup_failure_database_not_shared
        is SyncFailure.MappingBroken -> R.string.setup_failure_mapping_broken
        SyncFailure.Offline -> R.string.setup_failure_offline
        SyncFailure.NotionUnavailable -> R.string.setup_failure_notion_unavailable
        SyncFailure.AppOutOfDate -> R.string.setup_failure_app_out_of_date
    }

/**
 * One mapping problem as a line of text.
 *
 * A deleted property has no name to show — it is gone from the schema — so those lines are
 * deliberately vaguer than the retyped ones, which can still name the column.
 */
@Composable
private fun MappingIssue.describe(): String =
    when (this) {
        is MappingIssue.DayKeyMissing -> stringResource(R.string.setup_issue_day_key_missing)
        is MappingIssue.DayKeyRetyped -> stringResource(R.string.setup_issue_day_key_retyped, actualType)
        is MappingIssue.HabitMissing -> stringResource(R.string.setup_issue_habit_missing)
        is MappingIssue.HabitRetyped -> stringResource(R.string.setup_issue_habit_retyped, name, actualType)
        MappingIssue.WrongDataSource -> stringResource(R.string.setup_issue_wrong_data_source)
    }

/**
 * A row the user picks exactly one of.
 *
 * The whole row is the target rather than the control, so the tap area is the full width and
 * comfortably past the 48dp `CLAUDE.md` §2 asks for. `Role.RadioButton` on the row and `null` on the
 * button itself is what stops a screen reader announcing the same thing twice.
 */
@Composable
fun SetupChoiceRow(
    label: String,
    caption: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(vertical = Spacing.cardGap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline,
                ),
        )
        RowLabel(label = label, caption = caption)
    }
}

/** A row the user ticks any number of. */
@Composable
fun SetupToggleRow(
    label: String,
    caption: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(vertical = Spacing.cardGap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline,
                    checkmarkColor = HabitsTheme.colors.text.primary,
                ),
        )
        RowLabel(label = label, caption = caption)
    }
}

/**
 * A row that is only there to be read — a column the mapping cannot use.
 *
 * Dimmed and with no control at all, rather than a disabled checkbox: a disabled control invites
 * the user to work out how to enable it, and there is no way to.
 */
@Composable
fun SetupReadOnlyRow(
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.cardGap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.text.tertiary,
            modifier = Modifier.weight(1f),
        )
        Text(text = caption, style = HabitsTheme.textStyles.monoSmall, color = HabitsTheme.colors.text.propertyChip)
    }
}

/**
 * The name of a Notion column, and its type beside it.
 *
 * ⚠ **The name is rendered exactly as Notion sent it**, emoji and all — `docs/notion-schema.md`
 * §2.3: it is stored verbatim and never normalised. Nothing here trims, cases or strips it.
 */
@Composable
private fun RowLabel(
    label: String,
    caption: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.yearDotGap)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.text.primary,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = HabitsTheme.textStyles.monoSmall,
                color = HabitsTheme.colors.text.propertyChip,
            )
        }
    }
}

/** The wizard's progress: one filled dot per completed step. */
@Composable
fun SetupProgressDots(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.weekPillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val isReached = index < current
            Box(
                modifier =
                    Modifier
                        .size(Sizing.statusDot)
                        .background(
                            color =
                                if (isReached) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            shape = CircleShape,
                        ),
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.35f
private const val BANNER_FILL_ALPHA = 0.08f
private const val BANNER_BORDER_ALPHA = 0.20f

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun SetupFailureBannerPreview() {
    HabitsTheme {
        Column(
            modifier = Modifier.padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            SetupFailureBanner(failure = SyncFailure.DatabaseNotShared)
            SetupPrimaryButton(text = "Continue", onClick = {})
            SetupPrimaryButton(text = "Continue", onClick = {}, isBusy = true)
        }
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun SetupRowsPreview() {
    HabitsTheme {
        Column(modifier = Modifier.padding(Spacing.screenPadding)) {
            SetupChoiceRow(label = "Date", caption = "date", selected = true, onSelect = {})
            SetupToggleRow(label = "📖 Read", caption = null, checked = true, onToggle = {})
            SetupToggleRow(label = "✒️ Journal", caption = null, checked = false, onToggle = {})
            SetupReadOnlyRow(label = "Progress", caption = "formula")
        }
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun SetupTextFieldPreview() {
    HabitsTheme {
        Column(modifier = Modifier.padding(Spacing.screenPadding)) {
            SetupTextField(
                label = "NOTION TOKEN",
                value = "ntn_not_a_real_token",
                onValueChange = {},
                placeholder = "ntn_…",
                helper = "Notion shows it once.",
                isSecret = true,
            )
        }
    }
}
