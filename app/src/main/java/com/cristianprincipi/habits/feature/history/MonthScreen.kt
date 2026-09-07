package com.cristianprincipi.habits.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianprincipi.habits.core.ui.PullToSync

/**
 * Monthly completion density for every habit at once — `docs/design-spec.md` §2.
 *
 * Stateful half only: it collects the state and forwards events. Everything drawn is in
 * [MonthContent], which is the split `CLAUDE.md` §6 asks for and the reason that file's previews can
 * exist without a ViewModel.
 *
 * There is no snackbar and no one-shot channel, unlike `WeekScreen`: nothing on this screen writes
 * anything, so there is nothing to confirm and nothing to undo.
 */
@Composable
fun MonthScreen(
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PullToSync(modifier = modifier) {
        MonthContent(
            state = state,
            onEvent = viewModel::onEvent,
            onOpenSyncStatus = onOpenSyncStatus,
        )
    }
}
