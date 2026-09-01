package com.cristianprincipi.habits.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * One habit's completion rates, streaks and year at a glance — `docs/design-spec.md` §5.
 *
 * Stateful half only: it collects the state. Everything drawn is in [StatsContent], which is the
 * split `CLAUDE.md` §6 asks for and the reason that file's previews can exist without a ViewModel.
 *
 * ⚠ **The last `StubScreen` in the shell, replaced here** — M5.3 put one on every tab and M9.1 is
 * where the fifth and last of them goes.
 */
@Composable
fun StatsScreen(
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    StatsContent(state = state, onOpenSyncStatus = onOpenSyncStatus, modifier = modifier)
}
