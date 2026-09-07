package com.cristianprincipi.habits.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianprincipi.habits.core.ui.PullToSync

/**
 * A year of completions as a dot grid — `docs/design-spec.md` §4.
 *
 * Stateful half only: it collects the state. Everything drawn is in [YearContent], which is the
 * split `CLAUDE.md` §6 asks for and the reason that file's previews can exist without a ViewModel.
 *
 * The thinnest screen in the app, and deliberately: it forwards no events, because there are none.
 * Both grids are read-only — at 3–5px a cell cannot be tapped reliably, so check-off lives on the
 * Week pills (`CLAUDE.md` §1) — and the design gives this view no sub-navigation.
 */
@Composable
fun YearScreen(
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YearViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PullToSync(modifier = modifier) {
        YearContent(state = state, onOpenSyncStatus = onOpenSyncStatus)
    }
}
