package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.repository.HabitRepository
import javax.inject.Inject

/** Which way a habit is being moved in the list. */
enum class MoveDirection {
    Up,
    Down,
}

/**
 * Move one habit one place — M15.5.
 *
 * ### ⭐ Why this is a use case and not a repository method
 *
 * "Swap with the neighbour" is a rule about a list, not about storage. Keeping it here means it can
 * be tested as arithmetic — no database, no fake DAO — and it keeps
 * [HabitRepository.setHabitOrder] dumb enough to have one obvious implementation: write these ids
 * in this order. A repository that took `(habitId, direction)` would have to know what the list
 * currently looks like, which is the screen's business and not the store's.
 *
 * ### ⚠ It takes the list it is moving within, rather than reading one
 *
 * The caller is a screen that is already rendering that list, and passing it in is what makes the
 * move operate on **what the user is looking at**. Re-reading inside would open a window where a
 * sync landed between the tap and the write, and the habit would move relative to a list that was
 * never on screen — one place further than intended, or past a habit that had just appeared.
 *
 * ### Off either end is a no-op, not an error
 *
 * The screen disables the control at the ends, so this cannot normally be reached — and it must
 * still be harmless, because the state the screen renders from and the tap that arrives are two
 * different moments. Throwing here would turn a stale tap into a crash on the app's settings
 * screen.
 */
class MoveHabit
    @Inject
    constructor(
        private val habits: HabitRepository,
    ) {
        suspend operator fun invoke(
            current: List<Habit>,
            habitId: HabitId,
            direction: MoveDirection,
        ) {
            val reordered = current.moved(habitId, direction) ?: return
            habits.setHabitOrder(reordered.map(Habit::id))
        }
    }

/**
 * This list with [habitId] moved one place, or `null` when the move does nothing.
 *
 * `null` covers all three ways there is nothing to do — the habit is not in the list, it is already
 * first and moving up, or already last and moving down — because the caller does the same thing in
 * every case, and separating them would invent distinctions no one acts on.
 *
 * Internal rather than private so the arithmetic can be tested directly. It is the whole of what
 * this file decides; the class around it is two lines of plumbing.
 */
internal fun List<Habit>.moved(
    habitId: HabitId,
    direction: MoveDirection,
): List<Habit>? {
    val from = indexOfFirst { it.id == habitId }
    val to = if (direction == MoveDirection.Up) from - 1 else from + 1
    // ⚠ `from < 0` is tested here and **first**: a habit that is not in the list gives
    // `from = -1`, and for a one-element list moving *down* that puts `to` at 0, which is a
    // perfectly valid index. The short-circuit is what stops a habit nobody can see being
    // "moved" onto the first one.
    return if (from < 0 || to !in indices) {
        null
    } else {
        toMutableList().apply { add(to, removeAt(from)) }
    }
}
