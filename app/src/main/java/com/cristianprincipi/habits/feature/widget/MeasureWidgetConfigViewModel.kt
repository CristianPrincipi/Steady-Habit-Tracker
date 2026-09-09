package com.cristianprincipi.habits.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What the measure widget's configuration screen shows — [HabitWidgetConfigUiState]'s twin.
 *
 * ⚠⚠ **[loaded] is not ceremony.** The list arrives from Room asynchronously, so the initial value
 * is empty, and a screen that decided its empty state from `measures.isEmpty()` alone would flash
 * *"connect to Notion first"* at somebody who has two. "Nothing yet" and "nothing at all" are
 * different answers and only one of them is worth a sentence.
 */
internal data class MeasureWidgetConfigUiState(
    val measures: List<Measure> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * The tracked numbers, for the widget the user has just dropped on their home screen.
 *
 * ⭐ **No ordering of its own**, for the habit picker's reason: `MeasureRepository.observeMeasures()`
 * is already ordered by `sortOrder`, so the picker lists them the way Stats does. A sort here would
 * be a second place that order is decided.
 */
@HiltViewModel
internal class MeasureWidgetConfigViewModel
    @Inject
    constructor(
        measures: MeasureRepository,
    ) : ViewModel() {
        val state: StateFlow<MeasureWidgetConfigUiState> =
            measures
                .observeMeasures()
                .map { list -> MeasureWidgetConfigUiState(measures = list, loaded = true) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = MeasureWidgetConfigUiState(),
                )

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        }
    }
