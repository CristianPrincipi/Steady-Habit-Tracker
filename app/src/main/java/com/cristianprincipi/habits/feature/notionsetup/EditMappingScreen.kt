package com.cristianprincipi.habits.feature.notionsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.NotionFailureBanner
import com.cristianprincipi.habits.core.designsystem.component.SecondaryButton
import com.cristianprincipi.habits.core.designsystem.component.readingWidth
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.SyncFailure

/**
 * The mapping, editable without disconnecting — **the last step in M7** (ADR-079).
 *
 * Stateful half only. Everything drawn is [MapPropertiesStep], the wizard's own third step, because
 * this **is** that step reached from a different direction: the user chose the same title for it on
 * 2026-08-31. See [EditMappingViewModel] for why the state and events are shared rather than copied.
 *
 * @param onSaved called once the new mapping is stored and applied. The caller pops back to the
 *   habit list, which by then is already showing the new set — the user's decision over a
 *   confirmation screen, on the grounds that the list *is* the confirmation.
 */
@Composable
fun EditMappingScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditMappingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { onSaved() }
    }

    EditMappingContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * Everything the screen draws: a banner when something failed, and the mapping step.
 *
 * **No `windowInsetsPadding` here, unlike [NotionSetupContent].** The wizard is a gate that sits
 * outside the navigation shell and owns its own insets; this is a destination *inside* the shell,
 * and the shell pads it — the rule `StubScreen` states and M7.5 verified on a device.
 *
 * ### The three things it can be showing
 *
 * The properties arrive over the network, so an empty list means one of two different things and the
 * screen must not confuse them. Empty **and busy** is the schema still loading; empty and idle is a
 * read that failed, and [MapPropertiesStep] rendered then would claim the user's database has no
 * date column and no checkboxes — a confident lie about their data.
 */
@Composable
fun EditMappingContent(
    state: NotionSetupUiState,
    onEvent: (NotionSetupEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .readingWidth()
                .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        state.failure?.let { failure -> NotionFailureBanner(failure = failure) }

        when {
            state.properties.isNotEmpty() -> {
                MapPropertiesStep(state = state, onEvent = onEvent)
            }

            state.isBusy -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Sizing.iconMedium),
                        color = HabitsTheme.colors.text.secondary,
                        strokeWidth = Sizing.todayRing,
                    )
                }
            }

            // The schema could not be read. The banner above says why; this is the way to ask again
            // without leaving and coming back.
            else -> {
                SecondaryButton(
                    text = stringResource(R.string.setup_mapping_reload),
                    onClick = { onEvent(NotionSetupEvent.DatabaseConfirmed) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val previewProperties =
    listOf(
        NotionProperty("d", "Date", "date", NotionPropertyRole.DayKey),
        NotionProperty("r", "📖 Read", "checkbox", NotionPropertyRole.Habit),
        NotionProperty("j", "✒️ Journal", "checkbox", NotionPropertyRole.Habit),
        NotionProperty("t", "🥕 Diet", "checkbox", NotionPropertyRole.Habit),
        // M15.4 moved a number out of Unavailable. The fixture keeps a formula so the preview
        // still renders the "not available" block that `Weight` used to be its only example of.
        NotionProperty("w", "⚖️ Weight", "number", NotionPropertyRole.Measure),
        NotionProperty("s", "😴 Sleep", "number", NotionPropertyRole.Measure),
        NotionProperty("p", "Progress", "formula", NotionPropertyRole.Unavailable),
    )

@Preview(name = "Editing", backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun EditMappingPreview() {
    HabitsTheme {
        EditMappingContent(
            state =
                NotionSetupUiState(
                    step = NotionSetupStep.MapProperties,
                    properties = previewProperties,
                    dayKeyPropertyId = "d",
                    habitPropertyIds = setOf("r", "j", "t"),
                    previouslyMappedHabitIds = setOf("r", "j", "t"),
                    // One ticked with a unit, one unticked — so the preview shows both states of
                    // the box that is disabled rather than hidden.
                    measurePropertyIds = setOf("w"),
                    measureUnits = mapOf("w" to "kg"),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "Dropping a habit", backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun EditMappingDroppingPreview() {
    HabitsTheme {
        EditMappingContent(
            // ⭐ The state this screen exists to be careful about: Journal is mapped now and has just
            // been unticked, so the note above the button names it.
            state =
                NotionSetupUiState(
                    step = NotionSetupStep.MapProperties,
                    properties = previewProperties,
                    dayKeyPropertyId = "d",
                    habitPropertyIds = setOf("r"),
                    previouslyMappedHabitIds = setOf("r", "j", "t"),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "Schema unreadable", backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 400)
@Composable
private fun EditMappingFailedPreview() {
    HabitsTheme {
        EditMappingContent(
            state = NotionSetupUiState(step = NotionSetupStep.MapProperties, failure = SyncFailure.Offline),
            onEvent = {},
        )
    }
}
