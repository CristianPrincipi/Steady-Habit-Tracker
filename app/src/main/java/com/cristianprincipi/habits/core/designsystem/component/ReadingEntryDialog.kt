package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.datetime.shortDateLabel
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.domain.model.MeasureId
import java.time.LocalDate
import java.util.Locale

/**
 * Typing one number into one day — M16.2b, ADR-156 §2: *"Weight · Thu, Oct 15"*, a decimal field
 * with the unit, Save / Cancel, and Clear when the day has a value.
 *
 * ⚠ **Undesigned** — the prototype writes nothing at all — so it is built from what the app already
 * draws: the disconnect dialog's surface, shape and button colours, and the setup wizard's field.
 * Shown to the user before polishing (`CLAUDE.md` §2).
 *
 * ⭐ **One dialog, two doors (M16.3).** Week opens it from a cell; the number widget's corner square
 * opens it over the home screen for today. It lives here, and speaks in callbacks rather than either
 * screen's events, so the two cannot drift into two dialogs that ask the same question differently.
 *
 * - **The field starts with the day's value selected**, so typing replaces it and a correction is
 *   one gesture; an empty day starts empty. Focus and the decimal keyboard arrive with the dialog.
 * - **Save is disabled, not refused**, on anything that is not a number, and the field says why the
 *   moment there is something to say — never on an empty field (`ReadingEntry.isInvalid`).
 * - **Done on the keyboard saves**, when Save would.
 *
 * The text lives in [ReadingEntry] (the ViewModel's), and is mirrored here only to carry the cursor:
 * a `String` field cannot start with its contents selected.
 */
@Composable
fun ReadingEntryDialog(
    entry: ReadingEntry,
    locale: Locale,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember(entry.measureId, entry.date) {
        mutableStateOf(TextFieldValue(text = entry.text, selection = TextRange(0, entry.text.length)))
    }
    val focus = remember { FocusRequester() }
    val fieldDescription =
        stringResource(R.string.week_reading_field_description, entry.measureName, accessibleDate(entry.date, locale))
    val dateLabel = shortDateLabel(entry.date, locale)
    // At most one thing is wrong at a time, and a field that cannot be read outranks a save that failed.
    val problem =
        when {
            entry.isInvalid -> stringResource(R.string.week_reading_invalid)
            entry.saveFailed -> stringResource(R.string.week_reading_save_failed, entry.measureName, dateLabel)
            else -> null
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = stringResource(R.string.week_reading_title, entry.measureName, dateLabel),
                style = MaterialTheme.typography.titleMedium,
                color = HabitsTheme.colors.text.primary,
            )
        },
        text = {
            // Inside the dialog's own composition, not after `AlertDialog(…)`: the dialog is a
            // separate window, and a request made from the screen's composition runs before this
            // field is attached to it — the field opened unfocused, with no keyboard (seen on the
            // emulator at M16.2b).
            LaunchedEffect(focus) { focus.requestFocus() }
            OutlinedTextField(
                value = field,
                onValueChange = { changed ->
                    field = changed
                    onTextChange(changed.text)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { contentDescription = fieldDescription },
                textStyle = MaterialTheme.typography.bodyLarge,
                suffix = entry.unit.takeIf(String::isNotBlank)?.let { unit -> { Text(text = unit) } },
                supportingText = problem?.let { text -> { Text(text = text) } },
                isError = problem != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (entry.canSave) onSave() }),
                // The design's 14dp, as `SetupTextField` corrects it — Material's own tier is not it.
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
                        focusedSuffixColor = HabitsTheme.colors.text.secondary,
                        unfocusedSuffixColor = HabitsTheme.colors.text.secondary,
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = entry.canSave) {
                Text(text = stringResource(R.string.week_reading_save))
            }
        },
        dismissButton = {
            Row {
                if (entry.hadValue) {
                    TextButton(onClick = onClear) {
                        Text(
                            text = stringResource(R.string.week_reading_clear),
                            color = HabitsTheme.colors.text.secondary,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.week_reading_cancel), color = HabitsTheme.colors.text.secondary)
                }
            }
        },
    )
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 400)
@Composable
private fun ReadingEntryDialogPreview() {
    HabitsTheme {
        ReadingEntryDialog(
            entry =
                ReadingEntry(
                    measureId = MeasureId("weight"),
                    measureName = "Weight",
                    unit = "kg",
                    date = LocalDate.of(2026, 10, 15),
                    text = "82.9",
                    hadValue = true,
                ),
            locale = Locale.US,
            onTextChange = {},
            onSave = {},
            onClear = {},
            onDismiss = {},
        )
    }
}
