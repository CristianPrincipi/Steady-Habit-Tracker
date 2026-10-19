package com.cristianprincipi.habits.feature.widget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.designsystem.component.ReadingEntry
import com.cristianprincipi.habits.core.designsystem.component.prefillText
import com.cristianprincipi.habits.core.ui.launchWrite
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import com.cristianprincipi.habits.domain.usecase.SetMeasureReading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/** What [TodayReadingActivity] draws over the home screen. */
internal sealed interface TodayReadingUiState {
    /** The number and today's value are being read — a frame or two, and nothing is drawn. */
    data object Loading : TodayReadingUiState

    /** The dialog, as Week draws it. */
    data class Open(
        val entry: ReadingEntry,
    ) : TodayReadingUiState
}

internal sealed interface TodayReadingEvent {
    /**
     * The screen is up. It carries the locale because the field's first text is the stored value in
     * the reader's decimal separator, and a ViewModel has no observable locale of its own
     * (`rememberCurrentLocale`). Sent again on a rotation, and ignored then.
     */
    data class Started(
        val locale: Locale,
    ) : TodayReadingEvent

    data class TextChanged(
        val text: String,
    ) : TodayReadingEvent

    /** Save: write what the field holds. Ignored while it does not hold a number. */
    data object Saved : TodayReadingEvent

    /** Clear: today had a value and now has none — sent to Notion as empty, never as zero. */
    data object Cleared : TodayReadingEvent

    /** Cancel, the scrim, or back: nothing is written. */
    data object Dismissed : TodayReadingEvent
}

/**
 * Today's value for one number, typed from the home screen — M16.3, the number widget's square.
 *
 * ⭐⭐ **Week's dialog, Week's write, the habit square's sync.** The dialog is the shared
 * `ReadingEntryDialog`; the write is [SetMeasureReading], which every widget repaints after; and
 * the sync is asked for **at the tap**, while the app is awake, because that is the only time a
 * home-screen action can count on (ADR-152). ADR-156 §2 settled that last part in advance: *"Sync
 * timing = a check-off's."*
 *
 * ⚠ **The day is fixed when the dialog opens**, from [Clock], and named in its title. A dialog left
 * open across midnight still writes the day it says, rather than a day the user never saw.
 *
 * ### ⚠ It closes only once the write has landed, which is the opposite of Week
 *
 * Week closes first and lets the cell show the new value, with a snackbar if it fails. Here there is
 * nothing behind the dialog to show anything — so a failed write keeps the dialog open and says so in
 * the field ([ReadingEntry.saveFailed]), and Save tries again. The order also keeps this ViewModel
 * alive until the sync request is stored, since closing ends its scope.
 */
@HiltViewModel
internal class TodayReadingViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val measures: MeasureRepository,
        private val setMeasureReading: SetMeasureReading,
        private val syncStatus: SyncStatusRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val measureId = savedStateHandle.get<String>(TodayReadingActivity.EXTRA_MEASURE_ID)?.let(::MeasureId)
        private val entry = MutableStateFlow<ReadingEntry?>(null)
        private var started = false
        private val _closed = Channel<Unit>(Channel.CONFLATED)

        val uiState: StateFlow<TodayReadingUiState> =
            entry
                .map { open -> open?.let(TodayReadingUiState::Open) ?: TodayReadingUiState.Loading }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                    initialValue = TodayReadingUiState.Loading,
                )

        /** One-shot: the dialog is done, and the activity should go — the home screen is behind it. */
        val closed: Flow<Unit> = _closed.receiveAsFlow()

        fun onEvent(event: TodayReadingEvent) {
            when (event) {
                is TodayReadingEvent.Started -> {
                    start(event.locale)
                }

                is TodayReadingEvent.TextChanged -> {
                    // A new attempt clears the old failure: the field should say what is wrong now.
                    entry.update { open -> open?.copy(text = event.text, saveFailed = false) }
                }

                TodayReadingEvent.Saved -> {
                    // A disabled Save cannot send this; a stale one is ignored rather than writing
                    // whatever a half-typed field would parse to.
                    entry.value?.let { open -> open.value?.let { value -> write(open, value) } }
                }

                TodayReadingEvent.Cleared -> {
                    entry.value?.let { open -> write(open, value = null) }
                }

                TodayReadingEvent.Dismissed -> {
                    close()
                }
            }
        }

        /**
         * Opens the dialog for today, on the number the widget named — or closes at once when there is
         * no such number any more: a widget outlives its number (ADR-081), and an empty dialog for a
         * number that is gone would be a question with no answer.
         */
        private fun start(locale: Locale) {
            if (started) return
            started = true
            viewModelScope.launch {
                val today = LocalDate.now(clock)
                val measure = measureId?.let { id -> measures.observeMeasures().first().firstOrNull { it.id == id } }
                if (measure == null) {
                    close()
                    return@launch
                }
                val value =
                    measures
                        .observeReadings(from = today, to = today)
                        .first()
                        .firstOrNull { reading -> reading.measureId == measure.id }
                        ?.value
                entry.value =
                    ReadingEntry(
                        measureId = measure.id,
                        measureName = measure.name,
                        unit = measure.unit,
                        date = today,
                        text = prefillText(value, locale),
                        hadValue = value != null,
                    )
            }
        }

        private fun write(
            open: ReadingEntry,
            value: Double?,
        ) {
            launchWrite(onFailure = { entry.update { current -> current?.copy(saveFailed = true) } }) {
                setMeasureReading(measureId = open.measureId, date = open.date, value = value)
                requestSync()
                close()
            }
        }

        /**
         * Swallowed if it fails, for `WidgetCheckOff.requestSync`'s reason: the number is already in
         * Room and owed to Notion, and the next app-open or periodic run takes it. Reporting it would
         * be telling the user their number was not saved, when it was.
         */
        private suspend fun requestSync() {
            try {
                syncStatus.requestWidgetCheckOffSync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") throwable: Throwable,
            ) {
                // Nowhere to report it, and nothing lost — see above.
            }
        }

        private fun close() {
            _closed.trySend(Unit)
        }

        private companion object {
            /** `CLAUDE.md` §6's `WhileSubscribed(5_000)` — survives a rotation, stops on a real exit. */
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
