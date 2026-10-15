package com.cristianprincipi.habits.feature.widget

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * What the widget picker draws — M15.9.
 *
 * ⭐⭐ **The user's brief, and it is the whole design of this file**: *"The widget have to show the
 * new icon logo and a sample render of the habit widget (with the first habit as a sample, without
 * the real data)."* So a preview is their **first habit's identity** — its name, its emoji, its
 * colour — over a **history that never happened**. Both halves are deliberate: the name is what
 * makes the picker entry recognisably theirs, and the invented history is what keeps a home screen
 * seen over someone's shoulder from being a record of what they did and did not do.
 *
 * ⚠ **Nothing here is drawn by this file.** The picture is `WidgetBody`, the real one — see
 * `HabitGridWidget.providePreview`. These functions only build the state it is handed, which is why
 * a preview cannot drift from the widget: there is no second drawing to keep in step.
 */

/**
 * The habit widget's preview subject: the first habit in the user's own order, over a sample year.
 *
 * ⚠ **"First" is the user's order, not an arbitrary one.** `GetHabitsForYear` returns the list
 * already sorted by the `sortOrder` M15.5 made persistent, so this is the habit at the top of their
 * Week screen — the one they will recognise fastest in a picker.
 *
 * ⚠ It asks the same use case the widget itself asks, through the same entry point, rather than
 * reaching for a repository: `CLAUDE.md` §6 puts that read in `domain/usecase` precisely so a second
 * caller does not re-derive it. The cost is a trailing year computed to read three fields off it,
 * paid once per cold start.
 *
 * ⭐ **The fallback is not a failure case.** A phone that has never been connected to Notion has no
 * habits at all, and the picker is exactly where someone deciding whether to add a widget is
 * standing. It gets a sample rather than the *"nothing to show"* card the placed widget would draw.
 */
internal suspend fun sampleHabitWidgetState(context: Context): HabitWidgetState.Content {
    val deps =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            HabitWidgetEntryPoint::class.java,
        )
    val habit =
        deps
            .habitsForYear()()
            .first()
            .firstOrNull()
            ?.period
            ?.habit
    // ⚠ The real clock, for a sample year. The trailing year ends on the CURRENT week, so a grid
    // whose last column were full would end on a Sunday whatever day it is — which is the one
    // detail that would make the preview read as a picture rather than as the widget.
    val today = LocalDate.now(deps.clock())
    val futureDays = DAYS_PER_WEEK - today.dayOfWeek.value
    val year = sampleYear(futureDays = futureDays)
    return HabitWidgetState.Content(
        name = habit?.name ?: context.getString(R.string.widget_preview_habit_name),
        emoji = habit?.emoji ?: context.getString(R.string.widget_preview_habit_emoji),
        color = habit?.color ?: SAMPLE_HABIT_COLOR,
        completedThisWeek = SAMPLE_COMPLETED_THIS_WEEK,
        daysThisWeek = DAYS_PER_WEEK,
        year = year,
        today = sampleTodayCheck(year, futureDays, today),
    )
}

/**
 * The corner square of a sample — M15.11. **It agrees with the sample grid's own today**, for the
 * reason the grid ends on the real weekday: a square that said done beside a dot that said missed
 * would make the preview read as broken.
 *
 * ⚠⚠ **It never carries the real habit's id**, although the name and colour above are the real
 * habit's. A preview is a picture, but its square is the same control the placed widget draws, so an
 * id here would aim a sample's invented value at the user's real history. [SAMPLE_HABIT_ID] matches
 * no habit, and `WidgetCheckOff` writes nothing for a habit it cannot find.
 */
internal fun sampleTodayCheck(
    year: List<DayStatus>,
    futureDays: Int,
    today: LocalDate,
): TodayCheck =
    TodayCheck(
        habitId = SAMPLE_HABIT_ID,
        date = today,
        completed = year[year.size - futureDays - 1] == DayStatus.COMPLETED,
    )

/**
 * The measure widget's preview subject: the first tracked number, over a sample series.
 *
 * ### ⚠⚠ The anchor is rounded from the real average, and that is not a leak
 *
 * A sparkline's y is normalised to its own min–max (`sparklinePoints`), so the **curve** carries no
 * absolute value and any shape would do. The **badge** does not: it prints a figure, and that figure
 * has to be plausible for a unit this code does not know. A constant cannot be — `84.3` is a body
 * weight and nonsense for hours of sleep; `7.8` is the reverse.
 *
 * ⭐ So the sample is anchored on the real average **rounded to a whole unit**, then wandered around
 * it by [SAMPLE_MEASURE_DEVIATIONS]. No reading of theirs is drawn and the printed average is the
 * sample's own mean, not the real one — but *Weight* previews near a weight and *Sleep* near a
 * night's sleep, which is what stops the picker looking broken.
 *
 * ⚠ The points are evenly spaced, where real ones are not (`MeasureWidgetState.Content.points`).
 * That is honest for a sample: it describes a number recorded regularly, rather than claiming a
 * spacing that belongs to data this preview does not have.
 */
internal suspend fun sampleMeasureWidgetState(context: Context): MeasureWidgetState.Content {
    val deps =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MeasureWidgetEntryPoint::class.java,
        )
    val series =
        deps
            .measureSeries()()
            .first()
            .firstOrNull()
    val points =
        samplePoints(
            anchor =
                series
                    ?.average
                    ?.value
                    ?.roundToInt()
                    ?.toDouble() ?: SAMPLE_MEASURE_ANCHOR,
        )
    return MeasureWidgetState.Content(
        name = series?.measure?.name ?: context.getString(R.string.widget_preview_measure_name),
        color = series?.measure?.color ?: SAMPLE_MEASURE_COLOR,
        // The sample's own mean, so the badge and the curve describe the same numbers.
        average = points.map { point -> point.y.toDouble() }.average(),
        unit = series?.measure?.unit ?: context.getString(R.string.widget_preview_measure_unit),
        points = points,
    )
}

/**
 * A year of a habit that does not exist: [SAMPLE_COMPLETION_RATE] of it kept, the rest missed.
 *
 * @param futureDays how many days of the current week are still to come. The year arrives
 *   column-major and Monday-aligned, so those are exactly the **last** entries of the list — see
 *   `GridFit.lastWeeks`, which is why a multiple of seven is the only safe thing to drop.
 *
 * ⭐ **Seeded, so the picker does not shuffle.** A preview redrawn on every cold start with a
 * different scatter would read as data changing rather than as a sample. [SAMPLE_SEED] fixes it, and
 * `futureDays` cannot disturb it: the future branch is taken only for trailing indices, after every
 * random draw the earlier ones make.
 */
internal fun sampleYear(futureDays: Int): List<DayStatus> {
    val random = Random(SAMPLE_SEED)
    val total = WEEKS_IN_YEAR * DAYS_PER_WEEK
    return List(total) { index ->
        when {
            index >= total - futureDays -> DayStatus.FUTURE
            random.nextFloat() < SAMPLE_COMPLETION_RATE -> DayStatus.COMPLETED
            else -> DayStatus.INCOMPLETE
        }
    }
}

/**
 * A month of readings that were never taken, drifting gently down around [anchor].
 *
 * ⚠ **A trend rather than noise.** A sparkline's job is to show the *shape* of a change, and random
 * values at this width draw a hairball — the one picture that tells a viewer nothing about what the
 * widget is for.
 */
internal fun samplePoints(anchor: Double): List<Offset> =
    SAMPLE_MEASURE_DEVIATIONS.mapIndexed { index, deviation ->
        Offset(
            x = index.toFloat() / (SAMPLE_MEASURE_DEVIATIONS.size - 1),
            y = (anchor * (1 + deviation)).toFloat(),
        )
    }

/** Fixed so the same sample year is drawn every time; see [sampleYear]. */
private const val SAMPLE_SEED = 20_260_926

/** Ambitious but not perfect — a solid grid with visible gaps reads as a habit, not as a wall. */
private const val SAMPLE_COMPLETION_RATE = 0.64f

/** The subtitle under a sample name. Consistent with [SAMPLE_COMPLETION_RATE], near enough. */
private const val SAMPLE_COMPLETED_THIS_WEEK = 5

/** Only reached with no habits at all — see [sampleHabitWidgetState]'s note on the fallback. */
private val SAMPLE_HABIT_COLOR = HabitColor.GREEN

/** Not a Notion property id, so it can never name a real habit; see [sampleTodayCheck]. */
internal val SAMPLE_HABIT_ID = HabitId("widget-preview-sample")
private val SAMPLE_MEASURE_COLOR = HabitColor.PURPLE

/** Only reached with no tracked numbers at all, where there is no real average to round. */
private const val SAMPLE_MEASURE_ANCHOR = 72.0

/**
 * Fractions of [samplePoints]'s anchor: a slow decline with a real wobble on it.
 *
 * Written as deviations rather than values so the same shape works for a body weight, a sleep
 * duration or a step count without a second table.
 *
 * ### ⚠⚠ The rallies are spaced irregularly, and the first table got exactly this wrong
 *
 * A first cut alternated up and down at **every** step. On the phone it drew a **sawtooth** —
 * fourteen even teeth across 108dp — which is the hairball [samplePoints] warns about wearing a
 * tidier suit: perfectly regular, and telling a viewer nothing about what the widget is for.
 *
 * ⭐ So there are **two rallies in fourteen readings**, each two steps wide so it draws as a rounded
 * bump rather than a spike, and both smaller than the fall around them. That is a line going down
 * with the occasional good night in it, which is what a month of a real number looks like.
 * ⚠ `theSampleSeriesIsATrendRatherThanAComb` is what keeps it that way if this is ever retuned.
 *
 * Suppressed for `NICE_STEPS`' reason: these fourteen numbers **are** the drawing. Naming each one
 * would be fourteen constants used once, and the shape would be harder to read, not easier.
 */
@Suppress("MagicNumber")
private val SAMPLE_MEASURE_DEVIATIONS =
    listOf(
        0.015,
        0.012,
        0.008,
        0.010,
        0.013,
        0.009,
        0.004,
        -0.001,
        -0.005,
        -0.003,
        -0.008,
        -0.015,
        -0.021,
        -0.026,
    )
