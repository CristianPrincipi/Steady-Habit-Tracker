package com.cristianprincipi.habits.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A number the user tracks in Notion and wants averaged — `Weight`, `Sleep` (M15.4).
 *
 * ### ⚠ Deliberately not a [Habit], at every layer
 *
 * The two are alike enough to invite merging and must not be merged. A habit is checked off, has a
 * streak, has a completion rate, and is **written** to Notion. A measure is read, averaged, and
 * never written — the user's own scope: *"read only, just the average on stats"*.
 *
 * The separation is kept structurally rather than by discipline. Measure property ids live outside
 * `NotionPropertyMapping.habitPropertyIds`, readings live in their own tables, and `Measure` is its
 * own type — so the Week pills, the Month and Year dot grids and the streak tiles, all of which
 * iterate habits, cannot draw one by accident. There is no filter for a new screen to forget.
 *
 * ### What it does not have, and why
 *
 * **No start date.** A habit needs one because its completion rate is `completions ÷ days since
 * start`. An average over a fixed window needs no anchor, and a field that existed only to be
 * ignored would invite someone to compute a streak from a body weight.
 *
 * **No active flag**, for [Habit]'s reason: a measure whose column has left the mapped set is
 * hidden by the repository and never reaches this class (ADR-081), while its readings stay.
 */
data class Measure(
    val id: MeasureId,
    /** The property name with its leading emoji stripped, exactly as a habit's is. */
    val name: String,
    /** The leading emoji of the Notion property name, or empty. */
    val emoji: String,
    /**
     * What to write after the number — `kg`, `h` — or blank for none.
     *
     * ⚠⚠ **Typed by the user, because nothing can derive it.** The Notion schema carries no number
     * format, and `CLAUDE.md` §3.4 forbids inferring "kg" from a column *named* `Weight`.
     * `docs/notion-schema.md` §6 records the real answer from the owner — kg, and decimal hours —
     * and nothing in this codebase is allowed to know it.
     */
    val unit: String,
    /**
     * From the shared [HabitColor] palette, not a parallel one (M15.2).
     *
     * ⚠ Green and purple are each used twice in this app, once by a habit and once by a measure.
     * That is the user's decision and not a collision: a measure never appears in the same list,
     * grid or widget as a habit.
     */
    val color: HabitColor,
)

/**
 * A measure's local identity. [HabitId]'s twin, and a separate type for the same reason the models
 * are separate — a measure id passed where a habit id belongs would be a lookup that silently finds
 * nothing.
 */
@JvmInline
value class MeasureId(
    val value: String,
)

/**
 * One measure's reading on one day — 84.0 kg on the 7th of September.
 *
 * [Completion]'s twin, with the one difference that matters: a completion carries no value because
 * its existence *is* the value, while this carries a number. ⚠ What the two share is the rule that
 * **absence is the answer for a day without one** — there is no "no reading" value here, and a
 * missing day must never be read as `0.0`.
 *
 * A [LocalDate] and never an instant, for [Completion]'s reason: Notion's day key is date-only with
 * a null time zone, so the round trip has no timezone hazard in it.
 */
data class MeasureReading(
    val measureId: MeasureId,
    val date: LocalDate,
    val value: Double,
)

/**
 * One measure's average over a window, ready to draw.
 *
 * ### ⚠⚠ [value] is null when there is nothing to average, and that is not the same as zero
 *
 * A window with no readings has no average. Reporting `0.0` would draw "0.0 kg" — a confident,
 * specific, wrong number — where the honest answer is that the user has not weighed themselves
 * recently. This is the same distinction the storage layer makes by storing no row for a day with
 * no reading, carried up to where it is rendered.
 *
 * @param readings how many days in the window actually had one. Carried rather than derived because
 *   the tile says it: an average over three days and an average over thirty are different claims,
 *   and a number with no denominator invites the reader to assume the larger one.
 */
data class MeasureAverage(
    val measure: Measure,
    val value: Double?,
    val readings: Int,
) {
    init {
        require(readings >= 0) { "A window cannot contain a negative number of readings." }
        require((value == null) == (readings == 0)) {
            "An average exists exactly when there is something to average: value=$value, readings=$readings"
        }
    }

    companion object {
        /**
         * The mean of [values], or **no average at all** when there are none.
         *
         * ⭐ **The rule lives here because it now has two callers** — the Stats section and the
         * home-screen widget (M15.6d) — and it is the one rule in this feature that is worth
         * nothing if a third caller gets it differently. `values.average()` on an empty list is
         * `NaN`, which renders as "NaN kg"; `0.0` renders as a confident, specific, wrong number.
         * Neither is the answer, and the answer is that there is none.
         */
        fun of(
            measure: Measure,
            values: List<Double>,
        ): MeasureAverage =
            MeasureAverage(
                measure = measure,
                value = if (values.isEmpty()) null else values.average(),
                readings = values.size,
            )
    }
}

/**
 * One measure's readings across a window, and the mean of them — what a sparkline draws (M15.6d).
 *
 * ### ⚠⚠ The gaps are the point, and they are this feature's own invariant
 *
 * [readings] holds **one entry per day that actually had a reading, and nothing for the rest**.
 * That is [MeasureReading]'s rule carried up: a day with no weigh-in has no value, not a zero. A
 * user who weighs themselves weekly produces four entries in a thirty-day window, and anything that
 * draws this must place them **by their dates**. Spacing them evenly would draw four weigh-ins as a
 * month of steady change; reading a missing day as `0.0` would draw a cliff to the floor.
 *
 * ### [average] is computed here rather than passed in
 *
 * It is derivable from [readings], so accepting it as a parameter would be inviting a caller to
 * hand over a mean that disagrees with the points beside it. It uses [MeasureAverage.of], which is
 * the same rule the Stats screen's number goes through — the line and the figure printed against it
 * must never describe different data.
 *
 * @param readings **strictly increasing by date**, which is what a curve through them needs and
 *   what a range query naturally produces. Enforced rather than assumed, because a list arriving
 *   out of order would draw a plausible-looking wrong picture rather than fail.
 */
data class MeasureSeries(
    val measure: Measure,
    val from: LocalDate,
    val to: LocalDate,
    val readings: List<MeasureReading>,
) {
    init {
        require(!from.isAfter(to)) { "A window cannot end ($to) before it starts ($from)." }
        require(readings.all { it.measureId == measure.id }) {
            "A series holds one measure's readings, and ${measure.id.value} was given another's."
        }
        require(readings.all { !it.date.isBefore(from) && !it.date.isAfter(to) }) {
            "A series cannot hold readings from outside $from..$to"
        }
        require(readings.zipWithNext().all { (earlier, later) -> later.date > earlier.date }) {
            "A series must be strictly increasing by date, and got ${readings.map { it.date }}"
        }
    }

    /** The mean over the days that had a reading — never over the window. */
    val average: MeasureAverage = MeasureAverage.of(measure, readings.map { it.value })

    /**
     * Where [date] falls across the window: `0f` at [from], `1f` at [to].
     *
     * ⭐⭐ **This is what keeps the gaps honest, and it is why the window travels with the
     * readings.** Anything drawing this places a reading by the day it happened. Spacing the
     * readings evenly instead would draw a weekly weigh-in as a month of steady change — and a
     * caller that normalised against its own idea of the window could space them by a span the
     * query never used, which is the same lie with no way to notice it.
     *
     * A one-day window has nowhere to spread and answers `0f`; [MEASURE_WINDOW_DAYS] never produces
     * one, but the arithmetic must not divide by zero for a caller that does.
     */
    fun positionOf(date: LocalDate): Float {
        val span = ChronoUnit.DAYS.between(from, to)
        if (span == 0L) return 0f
        return ChronoUnit.DAYS.between(from, date).toFloat() / span.toFloat()
    }
}
