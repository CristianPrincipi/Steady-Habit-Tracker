package com.cristianprincipi.habits.feature.notionsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianprincipi.habits.core.designsystem.component.NotionFailureBanner
import com.cristianprincipi.habits.core.designsystem.component.readingWidth
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.domain.model.SyncFailure

/**
 * Paste a new token, keeping the database and mapping already set up — M12.7.
 *
 * See [ReplaceTokenViewModel] for why this exists: the wizard is a gate rather than a route, so
 * before this screen a token rotated in Notion could only be replaced by disconnecting, which threw
 * away a database choice and a property mapping that were never wrong.
 *
 * It is [EditMappingScreen]'s twin — that screen repairs the mapping without touching the token,
 * this one repairs the token without touching the mapping — and it is built the same way, by
 * rendering the wizard's own step so the two cannot drift apart.
 */
@Composable
fun ReplaceTokenScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReplaceTokenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { onSaved() }
    }

    ReplaceTokenContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * Everything the screen draws: a banner when the new token was refused, and the token step.
 *
 * **No `windowInsetsPadding`, unlike [NotionSetupContent]** — the wizard is a gate outside the
 * navigation shell and owns its insets; this is a destination *inside* the shell, and the shell pads
 * it. Same rule, and same reason, as [EditMappingContent].
 *
 * ⚠ **The banner is above the field, not below the button.** What it says is the reason the user is
 * on this screen, so it belongs where they start reading rather than where they finish.
 */
@Composable
fun ReplaceTokenContent(
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

        TokenStep(state = state, onEvent = onEvent)
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 640)
@Composable
private fun ReplaceTokenPreview() {
    HabitsTheme {
        ReplaceTokenContent(state = NotionSetupUiState(step = NotionSetupStep.Token), onEvent = {})
    }
}

/** The state a user most often arrives in: the old token was rotated away in Notion. */
@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 640)
@Composable
private fun ReplaceTokenRejectedPreview() {
    HabitsTheme {
        ReplaceTokenContent(
            state = NotionSetupUiState(step = NotionSetupStep.Token, failure = SyncFailure.TokenRejected),
            onEvent = {},
        )
    }
}
