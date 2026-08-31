package com.cristianprincipi.habits.core.ui.preview

import com.cristianprincipi.habits.core.datetime.monthWindow
import com.cristianprincipi.habits.core.datetime.trailingYearWindow
import com.cristianprincipi.habits.core.datetime.weekWindow
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.usecase.GetCompletionRate
import com.cristianprincipi.habits.domain.usecase.HabitYear
import com.cristianprincipi.habits.domain.usecase.buildPeriod
import com.cristianprincipi.habits.domain.usecase.byHabit
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * A realistic habit set with realistic history, for `@Preview`s and for tests.
 *
 * **Why this exists.** Every screen from M6 on renders a list of habits with months of completions
 * behind it, and a preview built from three hand-written completions shows none of the things that
 * actually go wrong: a ragged month, a streak that breaks mid-week, a habit that started part-way
 * through the window. Sparse fixtures make previews that look fine and screens that do not.
 *
 * **The five habits are the user's real ones**, with their exact property names taken from
 * `docs/notion-schema.md` §2.3 — which is itself taken from the raw bytes of a Notion export, not
 * transcribed from a screenshot. Two of the five emoji were misidentified that way once already, and
 * `Journal` carries a `U+FE0F` variation selector the others do not. They are written here as Kotlin
 * escapes for the same reason the schema doc gives: a mangled emoji still looks right on screen.
 *
 * **Deterministic, and that matters more than it sounds.** The completions come from a seeded
 * generator rather than `Random`, so a preview renders identically on every recomposition and two
 * runs of a screenshot test compare equal. A preview that reshuffles itself is a preview nobody can
 * review a change against.
 *
 * Nothing here reads a clock. Callers pass the day the data should end on, so a preview is as
 * reproducible in a year as it is today.
 */
object SampleHabits {
    /** The day the sample history ends on, when a caller does not name one. */
    val referenceDate: LocalDate = LocalDate.of(2026, 8, 22)

    /** The month [referenceDate] falls in — August 2026, which starts on a Saturday. */
    val referenceMonth: YearMonth = YearMonth.from(referenceDate)

    /**
     * The five habits, in the order the Notion schema lists them.
     *
     * Their start dates are staggered on purpose: `Focus` begins part-way through the year, so any
     * preview built from this set exercises `DayState.BEFORE_START` without the caller arranging it.
     */
    fun habits(reference: LocalDate = referenceDate): List<Habit> =
        listOf(
            sample("read", "📖 Read", HabitColor.BLUE, reference.minusDays(400)),
            sample("journal", "✒️ Journal", HabitColor.PURPLE, reference.minusDays(400)),
            sample("quote", "📜 Quote", HabitColor.GREEN, reference.minusDays(260)),
            sample("diet", "🥕 Diet", HabitColor.ORANGE, reference.minusDays(180)),
            sample("focus", "🎯 Focus", HabitColor.BLUE, reference.minusDays(95)),
        )

    /**
     * Completions for [habits], from each habit's start date up to [reference].
     *
     * Each habit gets its own keep-rate, so a screen showing all five shows a believable spread
     * rather than five identical densities: `Read` is kept most days, `Diet` about half.
     */
    fun completions(reference: LocalDate = referenceDate): List<Completion> =
        habits(reference).flatMap { habit ->
            val rate = KEEP_RATES.getValue(habit.id.value)
            generate(habit.id, from = habit.startDate, to = reference, keepRate = rate)
        }

    /**
     * The five habits as one week of [HabitPeriod]s, ready for a Week preview.
     *
     * ⭐ **It runs the real `buildPeriod`**, the same function `GetHabitsForWeek` uses, rather than
     * assembling cells by hand. A preview that built its own cells would keep rendering happily
     * after the thing that fills them in production broke — which is the one failure a preview
     * exists to catch. `buildPeriod` is `internal`, and this is in the same module, so there is
     * nothing to widen to reuse it.
     *
     * @param reference the day to treat as "today", so `FUTURE` and `BEFORE_START` both appear
     *   without the caller arranging them. The default sits mid-week on a Saturday, which is where
     *   one screen shows completed, incomplete and future days at once.
     * @param completions the history to draw against. Passed explicitly by anything that lets a
     *   preview be *changed* — `WeekPreviewActivity`'s in-memory check-off, for one.
     * @param weekOf which week to draw. Defaults to the week containing [reference]; a caller that
     *   pages passes the week it has paged to, while [reference] stays put.
     */
    fun weekPeriods(
        reference: LocalDate = referenceDate,
        completions: Collection<Completion> = completions(reference),
        weekOf: LocalDate = reference,
    ): List<HabitPeriod> {
        // Two dates, and they are not the same thing: [weekOf] picks which seven days to draw, while
        // [reference] stays "today". Paging away from today is exactly the case where conflating
        // them hides a bug — every day would go on looking present-tense.
        val window = weekWindow(weekOf)
        val byHabit = completions.toList().byHabit()
        return habits(reference).map { habit ->
            buildPeriod(
                habit = habit,
                window = window,
                completedDates = byHabit[habit.id].orEmpty(),
                today = reference,
            )
        }
    }

    /**
     * The same five habits over one calendar month, for the Month view's previews and tests.
     *
     * Defaults to the month [referenceDate] falls in, so the preview shows a month with a real
     * "today" in it: completed days behind it, `FUTURE` cells after it, and `Quote`, `Diet` and
     * `Focus` all starting partway through their own history.
     */
    fun monthPeriods(
        reference: LocalDate = referenceDate,
        completions: Collection<Completion> = completions(reference),
        monthOf: YearMonth = YearMonth.from(reference),
    ): List<HabitPeriod> {
        val window = monthWindow(monthOf)
        val byHabit = completions.toList().byHabit()
        return habits(reference).map { habit ->
            buildPeriod(
                habit = habit,
                window = window,
                completedDates = byHabit[habit.id].orEmpty(),
                today = reference,
            )
        }
    }

    /**
     * The same five habits over the trailing year ending at [reference], for the Year view.
     *
     * Builds its `CompletionRate` through the real `GetCompletionRate` on a clock fixed to
     * [reference] rather than recomputing the rule here. The rule is not obvious — the window starts
     * at `max(startDate, 365 days ago)`, which is why a young habit's percentage is over a short
     * denominator — and a second copy in preview data would be a second place to get it wrong.
     */
    fun years(
        reference: LocalDate = referenceDate,
        completions: Collection<Completion> = completions(reference),
    ): List<HabitYear> {
        val window = trailingYearWindow(reference)
        val byHabit = completions.toList().byHabit()
        val rateOf = GetCompletionRate(Clock.fixed(reference.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC))
        return habits(reference).map { habit ->
            val dates = byHabit[habit.id].orEmpty()
            HabitYear(
                period = buildPeriod(habit, window, dates, reference),
                rate = rateOf(completedDates = dates, startDate = habit.startDate),
            )
        }
    }

    /** One habit and its history — for a preview of a single card. */
    fun habitWithHistory(
        id: String = "read",
        reference: LocalDate = referenceDate,
    ): Pair<Habit, List<Completion>> {
        val habit = habits(reference).first { it.id.value == id }
        return habit to generate(habit.id, habit.startDate, reference, KEEP_RATES.getValue(id))
    }

    /**
     * A run of consecutive days ending on [reference] — for previewing a streak that is alive.
     *
     * The generated history above will not reliably contain one, and "current streak: 0" is the
     * least useful thing a stat tile preview can show.
     */
    fun unbrokenRun(
        id: String = "read",
        days: Int = 12,
        reference: LocalDate = referenceDate,
    ): List<Completion> = (0 until days).map { Completion(HabitId(id), reference.minusDays(it.toLong())) }

    private fun sample(
        id: String,
        propertyName: String,
        color: HabitColor,
        startDate: LocalDate,
    ): Habit {
        val emoji = propertyName.substringBefore(' ')
        return Habit(
            id = HabitId(id),
            name = propertyName.substringAfter(' '),
            emoji = emoji,
            color = color,
            startDate = startDate,
        )
    }

    /**
     * A seeded linear congruential generator, the same shape the prototype used for its demo data.
     *
     * Not `Random` and not `Random(seed)`: the seed is derived from the habit id, so each habit's
     * history is stable in isolation. Adding a sixth habit does not shift the other five, which is
     * what would otherwise make every existing preview change in a diff about something else.
     */
    private fun generate(
        habitId: HabitId,
        from: LocalDate,
        to: LocalDate,
        keepRate: Double,
    ): List<Completion> {
        var seed = habitId.value.fold(SEED_BASE) { acc, char -> acc + char.code }

        fun next(): Double {
            seed = (seed * MULTIPLIER + INCREMENT) and MASK
            return seed / MASK.toDouble()
        }
        return generateSequence(from) { day -> day.plusDays(1).takeIf { it <= to } }
            .filter { next() < keepRate }
            .map { Completion(habitId, it) }
            .toList()
    }

    private val KEEP_RATES =
        mapOf(
            "read" to 0.86,
            "journal" to 0.61,
            "quote" to 0.74,
            "diet" to 0.48,
            "focus" to 0.69,
        )

    private const val SEED_BASE = 1_013L
    private const val MULTIPLIER = 1_664_525L
    private const val INCREMENT = 1_013_904_223L
    private const val MASK = 0x7fffffffL
}
