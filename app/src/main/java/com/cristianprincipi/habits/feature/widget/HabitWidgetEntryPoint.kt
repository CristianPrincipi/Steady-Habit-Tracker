package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.domain.usecase.GetHabitsForWeek
import com.cristianprincipi.habits.domain.usecase.GetHabitsForYear
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * How the widget reaches the app's own use cases.
 *
 * ⚠⚠ **A `GlanceAppWidget` cannot be `@AndroidEntryPoint`** — it is not an Android component. The
 * `BroadcastReceiver` around it can be, but the widget object that actually needs the data is
 * constructed by us, so field injection never reaches it. `@EntryPoint` is the supported way in,
 * and it is the **first** one in this codebase: everything else so far has been a `ViewModel` or an
 * `Activity`, both of which Hilt can construct.
 *
 * ⭐ **It asks for use cases, not repositories or DAOs.** A widget is a read of the same two
 * questions the Year and Week screens ask, and `CLAUDE.md` §6 puts that logic in `domain/usecase`
 * precisely so a second caller does not re-derive it. In particular the trailing-year window and
 * the Monday alignment are `GetHabitsForYear`'s, not something the widget gets to have an opinion
 * about — a widget whose weeks started on Sunday would be a different picture of the same data.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface HabitWidgetEntryPoint {
    fun habitsForYear(): GetHabitsForYear

    fun habitsForWeek(): GetHabitsForWeek

    fun clock(): Clock
}
