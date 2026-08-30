package com.cristianprincipi.habits.feature.managehabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.usecase.GetAllHabits
import com.cristianprincipi.habits.domain.usecase.GetNotionDatabaseUrl
import com.cristianprincipi.habits.domain.usecase.SetHabitColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the user can do on the habit list. Which is one thing (ADR-010). */
sealed interface ManageHabitsEvent {
    /**
     * Give a habit a colour.
     *
     * The whole write surface of this screen, and deliberately: creating, renaming and deleting a
     * habit are Notion *schema* changes this app holds no code path for.
     */
    data class ColorPicked(
        val habitId: HabitId,
        val color: HabitColor,
    ) : ManageHabitsEvent
}

/**
 * Drives the habit list.
 *
 * ⭐ **Three use cases and no repository, and that is the point.** `SetHabitColor`'s KDoc asks for
 * exactly this: a screen that can set a colour should not be holding something that can also write
 * completions, and `GetNotionDatabaseUrl` keeps `disconnect()` out of reach for the same reason. The
 * classes are thin; the reach they deny is not.
 *
 * **Opening Notion is not an event here.** It is a URL handed to the browser by the composable that
 * has a `LocalUriHandler` — see [ManageHabitsScreen]. Routing it through this class would put an
 * Android `Intent` behind a ViewModel that `CLAUDE.md` §6 keeps free of `android.*`.
 */
@HiltViewModel
class ManageHabitsViewModel
    @Inject
    constructor(
        getAllHabits: GetAllHabits,
        getNotionDatabaseUrl: GetNotionDatabaseUrl,
        private val setHabitColor: SetHabitColor,
    ) : ViewModel() {
        val uiState: StateFlow<ManageHabitsUiState> =
            combine(getAllHabits(), getNotionDatabaseUrl()) { habits, url ->
                ManageHabitsUiState.Content(habits = habits, notionUrl = url)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = ManageHabitsUiState.Loading,
            )

        fun onEvent(event: ManageHabitsEvent) {
            when (event) {
                is ManageHabitsEvent.ColorPicked -> {
                    // Fire and forget, like every other write in this app: the local store is the
                    // source of truth, so the row it changes is already on its way back through the
                    // flow above. Nothing waits, and nothing needs to be told it worked.
                    viewModelScope.launch { setHabitColor(habitId = event.habitId, color = event.color) }
                }
            }
        }

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
