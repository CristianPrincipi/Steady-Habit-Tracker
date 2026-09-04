package com.cristianprincipi.habits.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Settings — `docs/design-spec.md` §6, and M11.1.
 *
 * Stateful half only; see [SettingsContent] for everything drawn, and for why this screen is a hub
 * rather than the prototype's one-screen connection form (`CLAUDE.md` §6).
 *
 * ⭐ **It stopped being a stub on 2026-09-04.** From M5.2 until then it was two rows and a title —
 * ADR-062's stopgap, put here because deleting `HabitsAppRoot` had left `SyncStatusScreen` (and with
 * it **disconnect**, which `CLAUDE.md` §3.6 promises is always available) unreachable. Both rows
 * survive M11.1; what they now sit under is the connection status card the design puts here.
 */
@Composable
fun SettingsScreen(
    onOpenSyncStatus: () -> Unit,
    onOpenHabits: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        state = state,
        onOpenSyncStatus = onOpenSyncStatus,
        onOpenHabits = onOpenHabits,
        modifier = modifier,
    )
}
