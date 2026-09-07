package com.cristianprincipi.habits.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianprincipi.habits.core.ui.PullToSync

/**
 * One habit's completion rates, streaks and year at a glance — `docs/design-spec.md` §5.
 *
 * Stateful half only: it collects the state. Everything drawn is in [StatsContent], which is the
 * split `CLAUDE.md` §6 asks for and the reason that file's previews can exist without a ViewModel.
 *
 * ⚠ **The last `StubScreen` in the shell, replaced here** — M5.3 put one on every tab and M9.1 is
 * where the fifth and last of them goes.
 *
 * One event reaches the ViewModel and it is the selector's: which habit the rest of the screen is
 * about. Everything under the pills is read-only (`CLAUDE.md` §1) — check-off lives on the Week
 * pills and the year grid here is the same read-only canvas the Year tab draws.
 */
@Composable
fun StatsScreen(
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PullToSync(modifier = modifier) {
        StatsContent(
            state = state,
            onEvent = viewModel::onEvent,
            onOpenSyncStatus = onOpenSyncStatus,
        )
    }
}
