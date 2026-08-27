package com.cristianprincipi.habits.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.PeriodCell

/*
 * The two places the domain meets the design system.
 *
 * `domain/` owns enums that carry no Android and no Compose — that is deliberate and load-bearing
 * (`HabitColor`'s own KDoc: *"an enum rather than a Compose `Color` so the domain layer stays free
 * of Android and of the design system"*, and `DayStatus`'s: *"deliberately not a `domain/model`
 * type"*). The consequence is that somebody has to join them, and this is that somebody.
 *
 * **It lives in `core/ui/` rather than in `feature/week/` because it is not the Week screen's.**
 * `PeriodCell` is explicitly *"one cell of a Week, Month or Year grid"*, and M8's two views plus
 * Stats' year grid need exactly these two functions. Written once here, not three times there.
 */

/**
 * A habit's colour, resolved against the theme.
 *
 * `@ReadOnlyComposable` because it only reads a composition local — it never emits, so it costs
 * nothing to call once per card per recomposition.
 */
@Composable
@ReadOnlyComposable
fun HabitColor.resolve(): Color {
    val palette = HabitsTheme.colors.habit
    return when (this) {
        HabitColor.BLUE -> palette.blue
        HabitColor.GREEN -> palette.green
        HabitColor.ORANGE -> palette.orange
        HabitColor.PURPLE -> palette.purple
    }
}

/**
 * What a grid cell looks like.
 *
 * The four-to-five widening the two enums' KDocs both describe: a domain [DayState] never has to
 * describe the absence of a day, because [PeriodCell.Padding] models that as its own case, and the
 * design system's [DayStatus.PADDING] is where those two facts meet.
 *
 * **A Week row never produces [DayStatus.PADDING]** — `weekWindow` is explicit that a week is
 * aligned to the week start by definition and so has no padding. The Month and Year grids do.
 */
fun PeriodCell.toDayStatus(): DayStatus =
    when (this) {
        PeriodCell.Padding -> {
            DayStatus.PADDING
        }

        is PeriodCell.Day -> {
            when (state) {
                DayState.COMPLETED -> DayStatus.COMPLETED
                DayState.INCOMPLETE -> DayStatus.INCOMPLETE
                DayState.FUTURE -> DayStatus.FUTURE
                DayState.BEFORE_START -> DayStatus.BEFORE_START
            }
        }
    }
