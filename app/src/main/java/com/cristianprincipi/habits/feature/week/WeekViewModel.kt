package com.cristianprincipi.habits.feature.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.datetime.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.datetime.TodayFlow
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.launchWrite
import com.cristianprincipi.habits.core.ui.observeEmptyReason
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureWeek
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import com.cristianprincipi.habits.domain.usecase.GetHabitsForWeek
import com.cristianprincipi.habits.domain.usecase.GetMeasuresForWeek
import com.cristianprincipi.habits.domain.usecase.SetMeasureReading
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives the Week screen — the landing tab, and the only place a habit is checked off (ADR-009).
 *
 * Two inputs that change and one that streams. Which week is showing lives in [anchor] rather than
 * in a parameter, because `‹` and `›` have to move it and nothing outside this screen has an opinion
 * about it; `flatMapLatest` then swaps the underlying query whenever it moves, cancelling the week
 * the user just paged away from.
 *
 * `GetHabitsForWeek` does the real work — it is the thing that joins habits to completions and
 * decides what each of the seven cells means. This class picks a week, labels the result,
 * forwards five gestures, and says once what each check-off changed.
 *
 * ### ⭐ Midnight is an input, not a moment that gets missed — M6.9, ADR-073
 *
 * [WeekUiState.Content.isCurrentWeek] and `HabitPeriod.todayIndex` are both *emission-time* facts:
 * they describe the day the value was built on. That is fine as long as a new value gets built when
 * the day changes, and nothing used to do that — an app left open across midnight kept yesterday's
 * ring and went on calling last week "this week" for ever.
 *
 * ⚠ **M6.7 turned that from a cosmetic staleness into a dead screen.** Pills are disabled by
 * `DayStatus.FUTURE`, so after midnight the new today was still drawn — and still gated — as
 * tomorrow. The first check-off of the day did nothing at all, silently, which is the one
 * interaction this whole app exists for.
 *
 * [TodayFlow] closes it by making today an input. Every day change re-emits, so the ring moves, the
 * pill becomes writable, and a week that has stopped being the current one says so.
 *
 * ⚠ **The week follows only if the user has not paged away** — see [anchor], where `null` carries
 * exactly that.
 *
 * ### A check-off on another day says what it changed, once
 *
 * An accepted write sends a [WeekMessage] down [messages], which `WeekScreen` turns into the undo
 * snackbar (M6.6). It is a `Channel` rather than a field on [WeekUiState] because it must be said
 * exactly once — `CLAUDE.md` §6's rule for one-shots, and the reason a rotation does not re-announce
 * a tap the user made a minute ago.
 *
 * ⚠ **A tap on today says nothing at all** — the user's M6.11 call, replacing M6.6's "every write".
 * See [write] for why the split falls there and not somewhere else.
 *
 * ### ⚠ A refused write still says nothing
 *
 * `ToggleCompletion` returns `false` when the date has not happened yet, and [onEvent] sends no
 * message for it. **M6.7 made future pills untappable** (ADR-071), so nothing a finger can do
 * reaches that branch any more — and a sentence explaining a tap the user could not make would say
 * less than the dimmed pill already does. The guard stays where it is regardless: it is the
 * domain's rule, the pill only mirrors it, and if the two ever disagree the pill is the bug.
 *
 * ### ⭐ A number is typed through a dialog this class holds — M16.2b
 *
 * A number's cell opens [WeekUiState.Content.readingEntry]; Save writes through [SetMeasureReading]
 * and closes it. The day's habit cards are not involved, and nor is the undo snackbar: the cell
 * showing the new value is the confirmation. A write that fails says so ([WeekMessage.ReadingSaveFailed]).
 *
 * ### An empty screen is the one place sync status is read
 *
 * With no habits in Room there is nothing to draw and nothing to explain it, so [emptyReason] says
 * whether the first import is on its way or has failed. It gates nothing — see that property.
 */
@HiltViewModel
class WeekViewModel
    @Inject
    // One input per thing this screen shows or does: the habits, the numbers, the two writes, the
    // empty screen's sync status, and the day in two forms (M6.9). A facade over two of them would
    // exist only for the lint count — `SyncEngine` declines one for the same reason.
    @Suppress("LongParameterList")
    constructor(
        private val getHabitsForWeek: GetHabitsForWeek,
        // M16.1 — the number rows under the cards, and since M16.2b the write for one of their cells.
        private val getMeasuresForWeek: GetMeasuresForWeek,
        private val setMeasureReading: SetMeasureReading,
        private val toggleCompletion: ToggleCompletion,
        // Read for one branch only — see `emptyReason`. Never awaited, never a gate (`CLAUDE.md` §1).
        private val syncStatus: SyncStatusRepository,
        // M6.9. The clock below answers "what day is it"; this one says when that answer changes.
        private val today: TodayFlow,
        // Injected rather than read, so "which week is this?" is a testable claim (`CLAUDE.md` §6).
        private val clock: Clock,
    ) : ViewModel() {
        /**
         * Which week the user has pinned, or `null` for **whichever week contains today**.
         *
         * ⭐⭐ **`null` is the whole of M6.9's answer, and it is why this is not a `LocalDate`.**
         * It used to hold the shown week outright, resolved once from "today" at construction — so
         * an app open across midnight went on showing the week that *was* current, for ever. Making
         * "I am following today" a value rather than a coincidence means the rollover needs no
         * bookkeeping at all: [snapshot] recomputes the week from whatever [today] last said, and a
         * user who paged away holds a date here that midnight cannot touch.
         *
         * **Always a week start when it is non-null**, and it stays one without being re-checked.
         * [moveWeeks] is the only writer of a concrete value and it steps ±7 days from a week start,
         * which lands on a week start. It matters because [WeekUiState.Content.weekStart] is what the
         * `‹ Aug 17 – Aug 23 ›` label is derived from while the seven cells come from
         * `GetHabitsForWeek` — those two agree by construction rather than by coincidence.
         *
         * ⚠ **And `null` means exactly "the current week", never merely "unset"** — [moveWeeks]
         * normalises back to it, so paging away and back is indistinguishable from never having
         * paged, and behaves the same at midnight. Without that, a round trip would leave a pinned
         * date that happened to equal today's week and would quietly stop following.
         */
        private val anchor = MutableStateFlow<LocalDate?>(null)

        /** The week on screen, and the day that decides which of its cells is today. */
        private data class Snapshot(
            val week: LocalDate,
            val today: LocalDate,
        )

        /**
         * Which week to draw, recomputed every time either input moves.
         *
         * Carrying [today] alongside the week rather than reading a clock further down is the point:
         * `isCurrentWeek` and the ring then describe **the same instant as each other**, and a
         * Monday-to-Tuesday rollover — which does not change the week at all — still produces a
         * different value here, so it cannot be conflated away by anything downstream.
         */
        private val snapshot: Flow<Snapshot> =
            combine(anchor, today()) { pinned, today ->
                Snapshot(week = pinned ?: startOfWeek(today), today = today)
            }

        /** One emission of [visibleWeek]: the week, and everything drawn over it. */
        private data class VisibleWeek(
            val snapshot: Snapshot,
            val habits: List<HabitPeriod>,
            val measures: List<MeasureWeek>,
        )

        /**
         * The week on screen and the habits and numbers over it, as one value.
         *
         * `flatMapLatest` swaps the underlying queries whenever [snapshot] moves, cancelling the week
         * the user just paged away from. The snapshot is carried alongside the data rather than
         * read back afterwards, because by the time a slow query returns the user may have paged on
         * — pairing them here is what stops the label describing a different week from the pills
         * below it. ⚠ The numbers (M16.1) are combined **inside** the same switch for that reason:
         * combined outside it, a page could draw this week's pills over last week's values.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val visibleWeek: Flow<VisibleWeek> =
            snapshot.flatMapLatest { visible ->
                combine(getHabitsForWeek(visible.week), getMeasuresForWeek(visible.week)) { habits, measures ->
                    VisibleWeek(visible, habits, measures)
                }
            }

        /**
         * `SyncStatus` collapsed to the only two things the empty screen has words for (M6.8).
         *
         * Read **only** in the empty branch, and it is worth saying why that is not a contradiction
         * of `CLAUDE.md` §3.5's *"sync is invisible until it fails"*. It is invisible here too: with
         * habits in Room this value changes nothing on screen. What §3.5 forbids is sync failure
         * blocking the check-off loop, and an empty screen has no loop to block — it has a user
         * looking at nothing, owed the *"clear, human-readable message with a retry affordance"* the
         * same rule asks for.
         *
         * The collapsing itself, and the three narrowings inside it, moved to
         * [observeEmptyReason] in M8.1 — the Month view needs the identical derivation, and two
         * copies of "which sentence does an empty habit screen say" is exactly the pair that drifts.
         */
        private val emptyReason: Flow<HabitsEmptyReason> = syncStatus.observeEmptyReason()

        /** The number dialog, while it is open (M16.2b). Local, not derived — `SyncStatusViewModel`'s prompt. */
        private val readingEntry = MutableStateFlow<ReadingEntry?>(null)

        val uiState: StateFlow<WeekUiState> =
            combine(visibleWeek, emptyReason, readingEntry) { visible, reason, entry ->
                stateFor(visible, reason, entry)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                // Not an empty Content, and not Empty: neither is known yet. Starting at either
                // would flash a wrong screen on every cold start — Empty in particular reads as
                // "you have no habits", which is a sentence no one should see while loading.
                initialValue = WeekUiState.Loading,
            )

        /**
         * ⚠ **`CONFLATED`, so a fast hand does not build a queue of stale offers.**
         *
         * Checking off five habits in a row is the normal way this screen is used, not an edge
         * case. A buffered channel would hold all five and the screen would walk through them one
         * after another, each undo arriving seconds after the tap it belongs to and the last one
         * long after the user had moved on. Conflation keeps only the newest, which is the only one
         * whose sentence is still true on screen.
         *
         * What that gives up is real and worth stating: **the older taps lose their Undo button.**
         * They keep the better one — the pill itself, which is still right there and still flips
         * back. Losing a shortcut beats a backlog of sentences about things that happened a while
         * ago (`CLAUDE.md` §3.5's principle, applied to the screen rather than to sync).
         *
         * It also means a message that arrives while nothing is collecting is replaced rather than
         * stacked, so returning to the tab cannot dump a history at the user.
         */
        private val _messages = Channel<WeekMessage>(Channel.CONFLATED)

        /** The one-shot stream `WeekScreen` renders as the undo snackbar (`CLAUDE.md` §6). */
        val messages: Flow<WeekMessage> = _messages.receiveAsFlow()

        fun onEvent(event: WeekEvent) {
            when (event) {
                is WeekEvent.DayToggled -> {
                    write(event, announce = true)
                }

                // ⚠ **No message on success.** Sending one would offer to undo the undo, and the
                // next press would offer to undo that — see `WeekEvent.UndoPressed`. A *failure*
                // still speaks: see `write`.
                is WeekEvent.UndoPressed -> {
                    write(event, announce = false)
                }

                WeekEvent.PreviousWeekPressed -> {
                    moveWeeks(-1)
                }

                // Deliberately unbounded in both directions. Paging past the last habit's start date
                // shows a week of BEFORE_START cells and paging into the future shows FUTURE ones —
                // both are honest, both are cheap, and a clamp would need a rule the design does not
                // give (`docs/design-spec.md` §3 draws only `‹` and `›`).
                WeekEvent.NextWeekPressed -> {
                    moveWeeks(+1)
                }

                // Back to following today, rather than to today's week as a fixed date — so the one
                // control whose whole promise is "put me back on the current week" keeps that
                // promise past midnight as well.
                WeekEvent.ThisWeekPressed -> {
                    anchor.value = null
                }

                is WeekEvent.Reading -> {
                    onReading(event)
                }
            }
        }

        /** The number dialog's events — M16.2b. */
        private fun onReading(event: WeekEvent.Reading) {
            when (event) {
                is WeekEvent.ReadingCellTapped -> {
                    openReading(event)
                }

                is WeekEvent.ReadingTextChanged -> {
                    readingEntry.update { entry -> entry?.copy(text = event.text) }
                }

                WeekEvent.ReadingSaved -> {
                    // A disabled Save cannot send this; a stale one is ignored rather than writing
                    // whatever a half-typed field would parse to.
                    readingEntry.value?.let { entry -> entry.value?.let { value -> closeAndWrite(entry, value) } }
                }

                WeekEvent.ReadingCleared -> {
                    readingEntry.value?.let { entry -> closeAndWrite(entry, value = null) }
                }

                WeekEvent.ReadingDismissed -> {
                    readingEntry.value = null
                }

                is WeekEvent.ReadingRetried -> {
                    writeReading(event, measureName(event.measureId))
                }
            }
        }

        /**
         * Opens the dialog for a cell — **only for a day that has happened**, on a number on screen.
         *
         * The cell for a future day is inert, so a tap on one does not reach here; this refuses it
         * anyway, for `ToggleCompletion`'s reason — the rule is the domain's ([SetMeasureReading]) and
         * the cell only mirrors it. Opening a dialog whose Save could only be refused would be a
         * control that lies.
         */
        private fun openReading(event: WeekEvent.ReadingCellTapped) {
            val week = measureOnScreen(event.measureId)
            val day = week?.days?.firstOrNull { it.date == event.date }
            if (week == null || day == null || day.isFuture) return
            readingEntry.value =
                ReadingEntry(
                    measureId = event.measureId,
                    measureName = week.measure.name,
                    unit = week.measure.unit,
                    date = event.date,
                    text = event.prefill,
                    hadValue = day.value != null,
                )
        }

        /** Closes first, so the dialog is gone the frame Save is pressed rather than after Room answers. */
        private fun closeAndWrite(
            entry: ReadingEntry,
            value: Double?,
        ) {
            readingEntry.value = null
            writeReading(WeekEvent.ReadingRetried(entry.measureId, entry.date, value), entry.measureName)
        }

        /**
         * One number into Room, and a sentence if it does not land. Silent on success — see
         * [WeekMessage.ReadingSaveFailed]. The retry it offers is this same write.
         */
        private fun writeReading(
            write: WeekEvent.ReadingRetried,
            measureName: String?,
        ) {
            launchWrite(
                onFailure = {
                    _messages.trySend(WeekMessage.ReadingSaveFailed(measureName, write.date, retry = write))
                },
            ) {
                setMeasureReading(measureId = write.measureId, date = write.date, value = write.value)
            }
        }

        private fun measureOnScreen(id: MeasureId): MeasureWeek? =
            (uiState.value as? WeekUiState.Content)?.measures?.firstOrNull { week -> week.measure.id == id }

        private fun measureName(id: MeasureId): String? = measureOnScreen(id)?.measure?.name

        /**
         * Calendar arithmetic, not instant arithmetic.
         *
         * `LocalDate.plusDays` counts days on a calendar, so a week that contains a daylight-saving
         * change is still seven days long. Adding `7 * 24` hours to an instant would not be.
         *
         * The step starts from [currentWeek] when nothing is pinned yet, which is how the first
         * `‹` off the landing screen knows where it is. Landing back **on** the current week clears
         * the pin instead of storing it — see [anchor]: a pinned date equal to today's week looks
         * identical on screen and behaves differently at midnight, and two states that render the
         * same must not be allowed to diverge later.
         */
        private fun moveWeeks(weeks: Long) {
            val current = currentWeek()
            val moved = (anchor.value ?: current).plusDays(weeks * DAYS_PER_WEEK)
            anchor.value = moved.takeIf { it != current }
        }

        /**
         * Today's week, read from the clock **now**.
         *
         * Safe here where it would not be in [snapshot]: an event happens at a moment, so reading the
         * clock is the correct answer to "which week is current *as the user presses this*".
         * Rendering is the case that needs [today] instead, because it outlives the moment.
         */
        private fun currentWeek(): LocalDate = startOfWeek(LocalDate.now(clock))

        /**
         * Performs one [WeekEvent.Write], and says something if it does not land.
         *
         * ### Everything the message needs is resolved **before** the coroutine starts
         *
         * The habit as it was named and the day as it stood when the finger landed. By the time the
         * write returns the state has already re-emitted, and a clock read after a suspend point
         * could answer a different day than the tap did — which is the bug M6.9 removed and this
         * would quietly reintroduce.
         *
         * ### ⚠⚠ The failure branch is not the success branch with a different sentence
         *
         * Two rules are deliberately inverted for it, and both inversions are in
         * [WeekMessage.SaveFailed]:
         *
         * - **A failed write speaks even on today**, where a successful one is silent (M6.11).
         * - **A failed undo speaks**, where a successful one is silent. Its retry is the
         *   [WeekEvent.UndoPressed] it came from, so pressing Retry cannot start the announcement
         *   loop `WeekEvent.UndoPressed` exists to prevent.
         *
         * ### ⚠ Why success is silent on today — the user's call at M6.11
         *
         * A sentence on **every** check-off is a sentence on the commonest action in the app, and
         * `MEMORY`/ADR-070 §2 had it against the user's stated taste for persistent state over
         * toasts. What the snackbar buys is *identification*, and identification is worth paying for
         * exactly where a mis-aim is plausible: today's column is the one the ring points at and the
         * one the landing tab exists to make trivial, while a tap on any other column is deliberate
         * back-fill onto an unmarked pill, where the neighbouring **column** and the neighbouring
         * **row** are the two mistakes this screen invites ([WeekMessage]).
         *
         * So the rule is a date comparison, not a new concept: **the day the user is obviously
         * aiming at gets no sentence; any other day gets named.** It applies to success only — a
         * write that never happened is invisible on every column alike.
         *
         * @param announce whether *success* is worth a sentence. Failure is always worth one.
         */
        private fun write(
            event: WeekEvent.Write,
            announce: Boolean,
        ) {
            val name = habitName(event.habitId)
            val toAnnounce = name?.takeIf { announce && event.date != LocalDate.now(clock) }
            launchWrite(
                onFailure = {
                    _messages.trySend(
                        WeekMessage.SaveFailed(
                            habitName = name,
                            date = event.date,
                            completed = event.completed,
                            retry = event,
                        ),
                    )
                },
            ) {
                // The write lands in Room and returns; Notion finds out when the sync engine next
                // runs (`CLAUDE.md` §1). The `false` means "that day has not happened yet" — no
                // write, and so nothing to announce or offer back.
                val written =
                    toggleCompletion(
                        habitId = event.habitId,
                        date = event.date,
                        completed = event.completed,
                    )
                if (written && toAnnounce != null) {
                    _messages.trySend(
                        WeekMessage.Toggled(
                            habitId = event.habitId,
                            habitName = toAnnounce,
                            date = event.date,
                            completed = event.completed,
                        ),
                    )
                }
            }
        }

        /**
         * The habit's name as this screen currently draws it, or `null` if it is not on screen.
         *
         * ⚠ **`null` means the tap came from a card that is not on screen**, which no gesture can
         * produce: the pill lives inside the very row this reads back. Returning null rather than
         * inventing a placeholder keeps the impossible case honest. Naming a habit "Habit" would be
         * worse than saying nothing, because the whole value of a [WeekMessage.Toggled] is the name
         * — and [WeekMessage.SaveFailed] has a sentence of its own for when there is none.
         */
        private fun habitName(id: HabitId): String? =
            (uiState.value as? WeekUiState.Content)
                ?.habits
                ?.firstOrNull { period -> period.habit.id == id }
                ?.habit
                ?.name

        private fun stateFor(
            week: VisibleWeek,
            reason: HabitsEmptyReason,
            entry: ReadingEntry?,
        ): WeekUiState {
            val visible = week.snapshot
            return if (week.habits.isEmpty()) {
                // Week-independent: habits are global, so an empty list here means there are no
                // active habits at all rather than none *this* week. `WeekUiState.Empty` explains
                // why that distinction matters, why it carries no week, and why the only thing that
                // reaches it is an import that has not landed yet.
                WeekUiState.Empty(reason)
            } else {
                WeekUiState.Content(
                    weekStart = visible.week,
                    habits = week.habits,
                    // Both against the snapshot's own day, not the clock's. Reading the clock here
                    // would reintroduce exactly the bug M6.9 removes: a second, later answer to
                    // "what day is it" inside a value built from an earlier one.
                    isCurrentWeek = visible.week == startOfWeek(visible.today),
                    currentYear = visible.today.year,
                    measures = week.measures,
                    readingEntry = entry,
                )
            }
        }

        private companion object {
            /** `CLAUDE.md` §6's `WhileSubscribed(5_000)` — survives a rotation, stops on a real exit. */
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
