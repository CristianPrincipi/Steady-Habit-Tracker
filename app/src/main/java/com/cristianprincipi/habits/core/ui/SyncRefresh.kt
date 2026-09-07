package com.cristianprincipi.habits.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.designsystem.component.PullToSyncBox
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Pull-to-refresh, wired — M15.3. Wrap a screen's content in this and it can be pulled to sync.
 *
 * ### ⭐⭐ Why this is one shared piece and not four copies
 *
 * Week, Month, Year and Stats all get the gesture (the user's decision, 2026-09-07 — over the
 * recommendation of Week alone). The state behind it is **not four states**: "a sync is running" is
 * one flow on one repository, identical whichever tab is on screen, and the action is one call.
 *
 * Threading it through four `UiState`s would have copied that logic four times and asked an awkward
 * question four times over — *what does "refreshing" mean while a screen is still `Loading` or
 * `Empty`?* — which is a question about the screen's own data and has nothing to do with sync. One
 * wrapper has no such question to answer.
 *
 * ⚠ **The deviation from `CLAUDE.md` §6, stated rather than hidden**: this puts a second `ViewModel`
 * inside a screen. It sits at the **screen root**, beside the screen's own — never below it — so
 * the rule it is bending is "one `UiState` per screen", not "no `ViewModel` below the screen root",
 * which still holds. The stateless half of every screen is untouched and still takes `state` and
 * `onEvent` and nothing else. Put to the user with both shapes side by side before it was written.
 *
 * ⚠ **It is scoped to the destination, like every other `hiltViewModel()` here** — so each tab has
 * its own instance reading the same repository flow, and there is no shared mutable state between
 * them to get out of step.
 */
@Composable
fun PullToSync(
    modifier: Modifier = Modifier,
    viewModel: SyncRefreshViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    PullToSyncBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier,
        content = content,
    )
}

/**
 * Whether a sync is running, and the one call that starts one.
 *
 * Deliberately the smallest possible surface: no `UiState`, no event type, no one-shot channel.
 * There is one boolean to render and one thing the user can do, and a sealed hierarchy for that
 * would be ceremony — the shapes `CLAUDE.md` §6 asks for exist to keep *screens* legible, and this
 * is not a screen.
 */
@HiltViewModel
class SyncRefreshViewModel
    @Inject
    constructor(
        private val syncStatus: SyncStatusRepository,
    ) : ViewModel() {
        /**
         * ⚠ **`Syncing` only, not "anything that is not `Idle`".**
         *
         * `WaitingForNetwork` is not a sync in flight — it is a sync that cannot start — and holding
         * the indicator up through it would spin for as long as the device is offline, which is the
         * opposite of telling the user something. `Failed` is likewise not running, and it already
         * has a surface of its own that says more than a spinner could.
         */
        val isRefreshing: StateFlow<Boolean> =
            syncStatus
                .observeStatus()
                .map { status -> status is SyncStatus.Syncing }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                    initialValue = false,
                )

        /**
         * Ask for a sync now.
         *
         * Not `launchWrite` (ADR-120) and deliberately not guarded: this enqueues work rather than
         * writing anything, `SyncScheduler.syncNow()` de-duplicates on a unique name, and a failure
         * to *start* a sync surfaces where every other sync failure already does.
         */
        fun refresh() {
            syncStatus.requestSync()
        }

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
