package com.cristianprincipi.habits.feature.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The landing tab, and the app's whole value proposition — `docs/design-spec.md` §3.
 *
 * Stateful half only: it collects the state and forwards events, and nothing else. Everything drawn
 * is in [WeekContent], which is the split `CLAUDE.md` §6 asks for and the reason that file's
 * previews can exist at all.
 *
 * ⚠ **No header yet.** `docs/design-spec.md` §1 gives every view a header with a title, and this one
 * a `‹ Aug 17 – Aug 23 ›` sub-nav row besides. **M6.4 builds both**, which is also when the three
 * paging events become reachable — [WeekViewModel] already handles them.
 */
@Composable
fun WeekScreen(
    modifier: Modifier = Modifier,
    viewModel: WeekViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WeekContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
