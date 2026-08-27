package com.cristianprincipi.habits.feature.notionsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.PrimaryButton
import com.cristianprincipi.habits.core.designsystem.component.SectionEyebrow
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSource

/*
 * The three steps of the connection wizard, plus the screen that says it worked.
 *
 * Each is stateless: state down, [NotionSetupEvent]s up (`CLAUDE.md` §6). None of them knows what a
 * data source is, what an HTTP status is, or what the user's columns are called until the state
 * hands them over — `CLAUDE.md` §3.4 makes the schema user data, and this is where that shows.
 */

/**
 * Step 1 — the token. Nothing has been sent to Notion when this renders.
 *
 * ⚠ **It scrolls, and that is not decoration — M5.5 watched it fail without it.** This is the only
 * screen in the app with a keyboard, `WindowInsets.safeDrawing` includes the IME, and the wizard
 * pads itself by `safeDrawing`. So raising the keyboard does not cover this step, it *shrinks* it —
 * and a plain Column given less room than its content simply clips the overflow. In landscape with
 * the keyboard up, the field, its helper line and **Continue** all vanished: a user could type a
 * token and had no way to submit it. Setup could not be completed at all.
 *
 * Exactly the defect ADR-055 fixed one step later, in the database picker, for the same reason.
 */
@Composable
fun TokenStep(
    state: NotionSetupUiState,
    onEvent: (NotionSetupEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.settingsSectionGap),
    ) {
        SetupHeading(
            title = stringResource(R.string.setup_token_title),
            body = stringResource(R.string.setup_token_body),
        )
        SetupTextField(
            label = stringResource(R.string.setup_token_label),
            value = state.token,
            onValueChange = { onEvent(NotionSetupEvent.TokenChanged(it)) },
            placeholder = stringResource(R.string.setup_token_placeholder),
            helper = stringResource(R.string.setup_token_helper),
            // ⚠ CLAUDE.md §3.6 — the token is a password and is never put on screen in the clear.
            isSecret = true,
        )
        PrimaryButton(
            text = stringResource(R.string.setup_token_continue),
            onClick = { onEvent(NotionSetupEvent.TokenSubmitted) },
            enabled = state.canContinueFromToken,
            isBusy = state.isBusy,
        )
    }
}

/**
 * Step 2 — which database.
 *
 * The paste-a-link field is folded away behind a text button rather than shown beside the list: it
 * is the exception, and `docs/design-spec.md` §6 is explicit that "a paste field is a fallback, not
 * the ideal". It earns its place because Notion's search is eventually consistent — a database
 * shared a minute ago can be missing from this list and perfectly reachable by id.
 *
 * A `LazyColumn` carrying its footer as the last item, the same shape [MapPropertiesStep] uses.
 * How many databases a token can reach is the user's business, not ours — `CLAUDE.md` §6's
 * unbounded list. A plain `Column` here put Continue below the fold with nothing to scroll, which
 * is a dead end, not a cosmetic bug. Keyed on **id, never title**: a workspace may hold two
 * databases with the same name, and this one does.
 */
@Composable
fun ChooseDatabaseStep(
    state: NotionSetupUiState,
    onEvent: (NotionSetupEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item(key = "heading") {
            SetupHeading(
                title = stringResource(R.string.setup_database_title),
                body = stringResource(R.string.setup_database_body),
                modifier = Modifier.padding(bottom = Spacing.settingsSectionGap),
            )
        }

        items(state.databases, key = { it.id }) { database ->
            SetupChoiceRow(
                label = database.title.ifBlank { stringResource(R.string.setup_database_untitled) },
                caption = null,
                selected = database.id == state.selectedDatabaseId,
                onSelect = { onEvent(NotionSetupEvent.DatabaseSelected(database.id)) },
            )
        }

        item(key = "footer") {
            Column(
                modifier = Modifier.padding(top = Spacing.settingsSectionGap),
                verticalArrangement = Arrangement.spacedBy(Spacing.settingsSectionGap),
            ) {
                if (state.isLinkFieldOpen) {
                    SetupTextField(
                        label = stringResource(R.string.setup_link_label),
                        value = state.databaseLink,
                        onValueChange = { onEvent(NotionSetupEvent.DatabaseLinkChanged(it)) },
                        placeholder = stringResource(R.string.setup_link_placeholder),
                        helper = stringResource(R.string.setup_link_helper),
                        errorText = stringResource(R.string.setup_link_unreadable).takeIf { state.isLinkUnreadable },
                    )
                    PrimaryButton(
                        text = stringResource(R.string.setup_link_submit),
                        onClick = { onEvent(NotionSetupEvent.DatabaseLinkSubmitted) },
                        enabled = state.canLookUpLink,
                        isBusy = state.isBusy,
                    )
                } else {
                    TextButton(onClick = { onEvent(NotionSetupEvent.LinkFieldOpened) }) {
                        Text(
                            text = stringResource(R.string.setup_database_use_link),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.setup_database_continue),
                        onClick = { onEvent(NotionSetupEvent.DatabaseConfirmed) },
                        enabled = state.canContinueFromDatabase,
                        isBusy = state.isBusy,
                    )
                }
            }
        }
    }
}

/**
 * Step 3 — the mapping. **The screen `CLAUDE.md` §3.4 exists for.**
 *
 * Three lists: the date columns, one of which is the day key; the checkbox columns, any of which
 * can be habits; and everything else, shown but not offered — which is how §3.2's "prevent a
 * read-only property being chosen rather than failing at sync time" is delivered.
 *
 * A `LazyColumn` with stable keys, per §6: the property list is unbounded in principle. So is step
 * two's database list — the claim that once stood here, that this was the only step that could run
 * long, was wrong, and M4.15 found it the hard way.
 */
@Composable
fun MapPropertiesStep(
    state: NotionSetupUiState,
    onEvent: (NotionSetupEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardContentGap),
    ) {
        item(key = "heading") {
            SetupHeading(
                title = stringResource(R.string.setup_mapping_title),
                body = stringResource(R.string.setup_mapping_body),
            )
        }

        item(key = "day-eyebrow") { SectionEyebrow(text = stringResource(R.string.setup_mapping_day_eyebrow)) }
        if (state.dayKeyCandidates.isEmpty()) {
            item(key = "day-empty") { StepNote(text = stringResource(R.string.setup_mapping_day_empty)) }
        }
        items(state.dayKeyCandidates, key = { "day-${it.id}" }) { property ->
            SetupChoiceRow(
                label = property.name,
                caption = property.typeName,
                selected = property.id == state.dayKeyPropertyId,
                onSelect = { onEvent(NotionSetupEvent.DayKeySelected(property.id)) },
            )
        }

        item(key = "habit-eyebrow") { SectionEyebrow(text = stringResource(R.string.setup_mapping_habits_eyebrow)) }
        if (state.habitCandidates.isEmpty()) {
            item(key = "habit-empty") { StepNote(text = stringResource(R.string.setup_mapping_habits_empty)) }
        }
        items(state.habitCandidates, key = { "habit-${it.id}" }) { property ->
            SetupToggleRow(
                label = property.name,
                caption = null,
                checked = property.id in state.habitPropertyIds,
                onToggle = { onEvent(NotionSetupEvent.HabitToggled(property.id)) },
            )
        }

        if (state.unavailableProperties.isNotEmpty()) {
            item(key = "unavailable-eyebrow") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                    SectionEyebrow(text = stringResource(R.string.setup_mapping_unavailable_eyebrow))
                    StepNote(text = stringResource(R.string.setup_mapping_unavailable_body))
                }
            }
            items(state.unavailableProperties, key = { "other-${it.id}" }) { property ->
                SetupReadOnlyRow(label = property.name, caption = property.typeName)
            }
        }

        item(key = "submit") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                if (state.habitPropertyIds.isEmpty() && state.habitCandidates.isNotEmpty()) {
                    StepNote(text = stringResource(R.string.setup_mapping_needs_habit))
                }
                PrimaryButton(
                    text = stringResource(R.string.setup_mapping_submit),
                    onClick = { onEvent(NotionSetupEvent.MappingSubmitted) },
                    enabled = state.canSaveMapping,
                    isBusy = state.isBusy,
                )
            }
        }
    }
}

/**
 * The end of the flow.
 *
 * **Zero days is a success, not an error.** A database whose pages all predate the app, or a brand
 * new one, imports nothing and is correctly connected — telling the user something went wrong would
 * be a lie, so the empty case gets its own sentence instead.
 */
@Composable
fun DoneStep(
    state: NotionSetupUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // The other plain-Column step, so the same clip is available to it — a short screen
                // today, but nothing stops the body from growing. One modifier against a class of
                // bug that only shows up on somebody else's device.
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.settingsSectionGap),
    ) {
        SetupHeading(
            title = stringResource(R.string.setup_done_title),
            body =
                if (state.importedDays == 0) {
                    stringResource(R.string.setup_done_body_empty)
                } else {
                    pluralStringResource(R.plurals.setup_done_body, state.importedDays, state.importedDays)
                },
        )
    }
}

/** A muted line of explanation inside a step. */
@Composable
private fun StepNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = HabitsTheme.colors.text.secondary,
    )
}

private val previewProperties =
    listOf(
        NotionProperty("d", "Date", "date", NotionPropertyRole.DayKey),
        NotionProperty("r", "📖 Read", "checkbox", NotionPropertyRole.Habit),
        NotionProperty("j", "✒️ Journal", "checkbox", NotionPropertyRole.Habit),
        NotionProperty("p", "Progress", "formula", NotionPropertyRole.Unavailable),
        NotionProperty("w", "Weight", "number", NotionPropertyRole.Unavailable),
    )

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun TokenStepPreview() {
    HabitsTheme {
        TokenStep(
            state = NotionSetupUiState(token = "ntn_not_a_real_token"),
            onEvent = {},
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun ChooseDatabaseStepPreview() {
    HabitsTheme {
        ChooseDatabaseStep(
            state =
                NotionSetupUiState(
                    step = NotionSetupStep.ChooseDatabase,
                    databases = listOf(NotionSource("a", "Habits"), NotionSource("b", "Reading log")),
                    selectedDatabaseId = "a",
                ),
            onEvent = {},
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun MapPropertiesStepPreview() {
    HabitsTheme {
        MapPropertiesStep(
            state =
                NotionSetupUiState(
                    step = NotionSetupStep.MapProperties,
                    properties = previewProperties,
                    dayKeyPropertyId = "d",
                    habitPropertyIds = setOf("r", "j"),
                ),
            onEvent = {},
        )
    }
}
