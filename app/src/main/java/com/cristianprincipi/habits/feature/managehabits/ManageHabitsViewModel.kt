package com.cristianprincipi.habits.feature.managehabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.ui.launchWrite
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.usecase.GetHabits
import com.cristianprincipi.habits.domain.usecase.GetMeasures
import com.cristianprincipi.habits.domain.usecase.GetNotionDatabaseUrl
import com.cristianprincipi.habits.domain.usecase.MoveDirection
import com.cristianprincipi.habits.domain.usecase.MoveHabit
import com.cristianprincipi.habits.domain.usecase.SetHabitColor
import com.cristianprincipi.habits.domain.usecase.SetMeasureColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the user can do on this list. Which is three things, none of them a schema change. */
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

    /**
     * Move a habit one place up or down — M15.5.
     *
     * ⭐ **Still not a schema change**, which is why it belongs on this screen beside the colour:
     * Notion has no order property (`docs/notion-schema.md` §3), so the order is app-local
     * presentation and nothing here is pushed. ADR-010 is untouched.
     */
    data class Moved(
        val habitId: HabitId,
        val direction: MoveDirection,
    ) : ManageHabitsEvent

    /**
     * Give a tracked number a colour — M15.6f.
     *
     * ⭐ **Its own event rather than a nullable id on [ColorPicked]**, because a `HabitId` and a
     * `MeasureId` are separate types for a reason (`Measure`'s KDoc): one event carrying either
     * would need a branch at the far end, and a branch is where the two get confused.
     */
    data class MeasureColorPicked(
        val measureId: MeasureId,
        val color: HabitColor,
    ) : ManageHabitsEvent
}

/**
 * Drives the habit list.
 *
 * ⭐ **Six use cases and no repository, and that is the point.** `SetHabitColor`'s KDoc asks for
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
        getHabits: GetHabits,
        getMeasures: GetMeasures,
        getNotionDatabaseUrl: GetNotionDatabaseUrl,
        private val setHabitColor: SetHabitColor,
        private val setMeasureColor: SetMeasureColor,
        private val moveHabit: MoveHabit,
    ) : ViewModel() {
        val uiState: StateFlow<ManageHabitsUiState> =
            combine(getHabits(), getMeasures(), getNotionDatabaseUrl()) { habits, measures, url ->
                ManageHabitsUiState.Content(habits = habits, measures = measures, notionUrl = url)
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
                    // ⚠ Quiet on failure — M13.12's ruling, and the screen is what makes it honest.
                    // The swatch is drawn from Room with no optimistic overlay, so a write that does
                    // not land leaves the old colour selected in front of the user. That is
                    // immediate, unambiguous feedback for a change that is decoration: the habit,
                    // its history and its sync are all untouched by it.
                    launchWrite { setHabitColor(habitId = event.habitId, color = event.color) }
                }

                is ManageHabitsEvent.MeasureColorPicked -> {
                    // Quiet on failure for `ColorPicked`'s reason, which holds here word for word:
                    // the swatch is drawn from Room with no optimistic overlay, so a write that did
                    // not land leaves the old colour selected in front of the user.
                    launchWrite { setMeasureColor(measureId = event.measureId, color = event.color) }
                }

                is ManageHabitsEvent.Moved -> {
                    // ⚠⚠ **The list is read from the state, not from the repository**, and that is
                    // what makes the move operate on what the user is looking at. Re-reading inside
                    // the use case would open a window where a sync landed between the tap and the
                    // write, and the habit would move relative to a list that was never on screen.
                    val current = (uiState.value as? ManageHabitsUiState.Content)?.habits ?: return
                    // Quiet on failure, for `ColorPicked`'s reason and more plainly: the row is
                    // drawn from Room with no optimistic overlay, so a write that does not land
                    // leaves the habit exactly where it was, in front of the user, which is
                    // unambiguous feedback for a change that is presentation.
                    launchWrite {
                        moveHabit(current = current, habitId = event.habitId, direction = event.direction)
                    }
                }
            }
        }

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
