package com.cristianprincipi.habits.feature.managehabits

import com.cristianprincipi.habits.domain.model.Habit

/**
 * What the habit list renders.
 *
 * ### ⚠ There is no `Empty` case, unlike the Week screen
 *
 * Week needed one because it is the landing tab: an empty Week is a blank app, and ADR-072 had to
 * work out *why* it was empty before it could say anything useful. Here the explanation is on the
 * screen whether or not there are habits — the card at the top says where habits come from — so an
 * empty list is already answered, and a state whose only difference is one line of text would be a
 * second way to say the same thing.
 *
 * ### ⚠ And no `Error` case, for `WeekUiState`'s reason
 *
 * Nothing on this path touches the network. The habits come from Room and the Notion link comes
 * from the saved mapping; a sync that cannot reach Notion is a fact about sync, and
 * `SyncStatusScreen` owns saying so (`CLAUDE.md` §3.5).
 */
sealed interface ManageHabitsUiState {
    /** Room has not emitted yet — brief, local, and never a spinner over the whole screen. */
    data object Loading : ManageHabitsUiState

    /**
     * @param habits the habit set as every other screen sees it (`GetHabits`). A habit whose
     *   property has left the mapped set is **not here** (ADR-081) — its row and history are kept,
     *   hidden, and come back with it.
     * @param notionUrl where the database lives, or `null` while the app has not learned it — a
     *   connection from an older build, or one whose first sync has not landed. The control is
     *   disabled in place rather than hidden (ADR-060).
     */
    data class Content(
        val habits: List<Habit>,
        val notionUrl: String?,
    ) : ManageHabitsUiState
}
