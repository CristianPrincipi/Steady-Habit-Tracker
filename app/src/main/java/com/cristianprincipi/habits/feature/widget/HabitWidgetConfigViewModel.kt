package com.cristianprincipi.habits.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What the widget's configuration screen shows.
 *
 * ⚠⚠ **[loaded] is not ceremony — without it the screen tells the user a lie for one frame.** The
 * habit list arrives from Room asynchronously, so the initial value is empty, and a screen that
 * decided its empty state from `habits.isEmpty()` alone would flash *"connect to Notion first"* at
 * somebody who has five habits. The two states are "nothing yet" and "nothing at all", and only one
 * of them is worth a sentence.
 */
internal data class HabitWidgetConfigUiState(
    val habits: List<Habit> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * The habit list, for the widget the user has just dropped on their home screen.
 *
 * ⭐ **No ordering of its own.** `HabitRepository.observeHabits()` is already ordered by
 * `HabitEntity.sortOrder` — the order the user set with M15.5's arrows — so the picker lists their
 * habits the way every other screen does. A sort here would be the fourth place that order is
 * decided and the one nobody would think to look at.
 */
@HiltViewModel
internal class HabitWidgetConfigViewModel
    @Inject
    constructor(
        habits: HabitRepository,
    ) : ViewModel() {
        val state: StateFlow<HabitWidgetConfigUiState> =
            habits
                .observeHabits()
                .map { list -> HabitWidgetConfigUiState(habits = list, loaded = true) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = HabitWidgetConfigUiState(),
                )

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        }
    }
