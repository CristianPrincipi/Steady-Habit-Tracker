package com.cristianprincipi.habits.domain.model

import java.time.LocalDate

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
}
