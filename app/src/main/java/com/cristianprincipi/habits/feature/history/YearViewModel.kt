package com.cristianprincipi.habits.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.datetime.TodayFlow
import com.cristianprincipi.habits.core.ui.observeEmptyReason
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import com.cristianprincipi.habits.domain.usecase.GetHabitsForYear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Year view's state — `docs/design-spec.md` §4.
 *
 * ### ⭐ Why `today` is injected even though nothing here is paged
 *
 * `GetHabitsForYear` reads the clock **once**, when it is called, and builds the window from it. A
 * ViewModel that called it once at construction would therefore hold a window that ends on the day
 * the screen was opened — and this is the tab most likely to be left sitting in the background,
 * because it is the one with nothing to do on it.
 *
 * So [today] is a re-entry trigger rather than a value: each new day re-invokes the use case, which
 * rebuilds the trailing 365 days from the new one. Without it the last column would quietly stop
 * being today and the whole grid would be a day stale, which is precisely the kind of wrongness
 * nobody notices in a picture made of 4dp squares.
 *
 * There is no `onEvent`, and that is not an omission: the design gives Year no sub-navigation, and
 * the grid is read-only because a 4dp cell cannot be aimed at (ADR-009).
 */
@HiltViewModel
class YearViewModel
    @Inject
    constructor(
        private val getHabitsForYear: GetHabitsForYear,
        private val syncStatus: SyncStatusRepository,
        private val today: TodayFlow,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<YearUiState> =
            combine(
                today().flatMapLatest { getHabitsForYear() },
                syncStatus.observeEmptyReason(),
            ) { habits, reason ->
                if (habits.isEmpty()) {
                    // No habits at all rather than none this year — see YearUiState.Empty.
                    YearUiState.Empty(reason)
                } else {
                    YearUiState.Content(habits)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = YearUiState.Loading,
            )

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
