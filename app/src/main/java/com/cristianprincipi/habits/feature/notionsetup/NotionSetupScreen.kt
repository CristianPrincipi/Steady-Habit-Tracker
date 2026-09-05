package com.cristianprincipi.habits.feature.notionsetup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.NotionFailureBanner
import com.cristianprincipi.habits.core.designsystem.component.readingWidth
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.domain.model.SyncFailure

/**
 * The Notion connection wizard.
 *
 * Stateful half: collects the state and forwards events, and nothing else — every decision lives in
 * [NotionSetupViewModel] and everything drawn lives in [NotionSetupContent], which is the split
 * `CLAUDE.md` §6 asks for and the reason the previews below can exist at all.
 */
@Composable
fun NotionSetupScreen(
    modifier: Modifier = Modifier,
    viewModel: NotionSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NotionSetupContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

/**
 * Everything the wizard draws.
 *
 * **System back moves one step back rather than leaving**, except on the first step, where there is
 * nothing behind it and the platform's own behaviour is right. `CLAUDE.md` §2 asks for system back
 * to be respected rather than fought; a wizard that ignored it and dropped the user out of setup
 * from step three would be the kind of thing that rule exists to prevent.
 *
 * `safeDrawing` insets rather than a `Scaffold`: there is no app bar and no bottom nav here — this
 * screen predates the navigation shell (M5) — so the content owns its own insets and never sits
 * under the status bar. It is **outside** the shell's graph (setup is a gate, ADR-061), so the
 * "shell owns insets" rule in `StubScreen` does not reach here and this is the exception it names.
 *
 * ### ⭐ The top row is pinned; each step scrolls itself
 *
 * `safeDrawing` includes the IME, so a raised keyboard *shrinks* this Column rather than covering
 * it — and whatever no longer fits is clipped, silently. The scrolling therefore has to live below
 * this level: [ChooseDatabaseStep] and [MapPropertiesStep] are `LazyColumn`s, which cannot be
 * nested inside a scrolling parent, so [TokenStep] and [DoneStep] each carry their own
 * `verticalScroll` instead. [SetupTopRow] stays put, being a few dp of progress dots.
 *
 * M5.5 found this by rotating the emulator with the keyboard up. See [TokenStep].
 */
@Composable
fun NotionSetupContent(
    state: NotionSetupUiState,
    onEvent: (NotionSetupEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canGoBack = state.step != NotionSetupStep.Token && state.step != NotionSetupStep.Done
    BackHandler(enabled = canGoBack) { onEvent(NotionSetupEvent.BackPressed) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // ⭐ M12.8, and **after** the insets: a cutout belongs to the window, not to the
                // column. Capping first would inset the centred column instead of the screen.
                .readingWidth()
                .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.settingsSectionGap),
    ) {
        SetupTopRow(state = state, onEvent = onEvent, canGoBack = canGoBack)

        state.failure?.let { failure -> NotionFailureBanner(failure = failure) }

        when (state.step) {
            NotionSetupStep.Token -> TokenStep(state = state, onEvent = onEvent)
            NotionSetupStep.ChooseDatabase -> ChooseDatabaseStep(state = state, onEvent = onEvent)
            NotionSetupStep.MapProperties -> MapPropertiesStep(state = state, onEvent = onEvent)
            NotionSetupStep.Done -> DoneStep(state = state)
        }
    }
}

/** Progress on the left, the step counter on the right, and Back below when there is a step to go back to. */
@Composable
private fun SetupTopRow(
    state: NotionSetupUiState,
    onEvent: (NotionSetupEvent) -> Unit,
    canGoBack: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetupProgressDots(current = state.step.number, total = NotionSetupStep.LAST_STEP)
            Text(
                text =
                    stringResource(
                        R.string.setup_step_counter,
                        state.step.number,
                        NotionSetupStep.LAST_STEP,
                    ),
                style = HabitsTheme.textStyles.eyebrow,
                color = HabitsTheme.colors.text.eyebrow,
            )
        }
        if (canGoBack) {
            TextButton(onClick = { onEvent(NotionSetupEvent.BackPressed) }) {
                Text(
                    text = stringResource(R.string.setup_back),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitsTheme.colors.text.secondary,
                )
            }
        }
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun NotionSetupTokenPreview() {
    HabitsTheme {
        NotionSetupContent(state = NotionSetupUiState(), onEvent = {})
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun NotionSetupNotSharedPreview() {
    HabitsTheme {
        NotionSetupContent(
            // ⭐ The screen's most important state: a valid token that can reach nothing, which
            // CLAUDE.md §3.3 says must never render as "not found".
            state = NotionSetupUiState(failure = SyncFailure.DatabaseNotShared),
            onEvent = {},
        )
    }
}
