package com.cristianprincipi.habits.feature.notionsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the mapping step **on its own**, for a connection that already exists (M7.8).
 *
 * ⭐⭐ **Why this exists at all.** Until now the mapping could only be reached by finishing first-time
 * setup, so a habit **added** in Notion could never be adopted and a habit **deleted** in Notion
 * halted sync with no way out but *disconnect → reconnect* — which wipes the token and costs the
 * user a paste of a credential only they have (ADR-079, `CLAUDE.md` §3.6). M7's Definition of Done
 * is not true without this.
 *
 * ⚠ **It never touches the token, and never asks for one.** That is the whole point. The token is
 * already stored and already valid; the only write here is [NotionSetupRepository.saveMapping].
 *
 * ### It shares [NotionSetupUiState] and [NotionSetupEvent] with the wizard, deliberately
 *
 * This screen *is* the wizard's third step, reached from a different direction — the user chose the
 * same title for it on 2026-08-31 for exactly that reason. Sharing the state means
 * [MapPropertiesStep] is rendered once rather than twice, and the two rules that populate it
 * ([withMapping]) cannot drift apart. The wizard fields this screen never uses — `token`,
 * `databases`, `importedDays` — stay at their defaults; `token` in particular is never written here
 * at all, which is the stronger form of `CLAUDE.md` §3.6.
 *
 * ⚠ **Only three events are handled**, because the mapping step has only three controls. The `else`
 * is the wizard's other seven, which cannot be fired by anything this screen draws.
 */
@HiltViewModel
class EditMappingViewModel
    @Inject
    constructor(
        private val repository: NotionSetupRepository,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                // Busy from the first frame: the schema is a network read, so the alternative is a
                // frame of "this database has no date column" at somebody whose database has one.
                NotionSetupUiState(step = NotionSetupStep.MapProperties, isBusy = true),
            )
        val uiState: StateFlow<NotionSetupUiState> = _uiState.asStateFlow()

        private val _saved = Channel<Unit>(Channel.BUFFERED)

        /**
         * Emits once the new mapping is stored **and** applied.
         *
         * A one-shot event rather than a flag on the state (`CLAUDE.md` §6): the screen leaves when
         * it arrives, and a state that said "saved" would re-fire the moment it was recomposed.
         */
        val saved: Flow<Unit> = _saved.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: NotionSetupEvent) {
            when (event) {
                is NotionSetupEvent.DayKeySelected -> {
                    _uiState.update { it.copy(dayKeyPropertyId = event.propertyId) }
                }

                is NotionSetupEvent.HabitToggled -> {
                    _uiState.update { it.withHabitToggled(event.propertyId) }
                }

                NotionSetupEvent.MappingSubmitted -> {
                    save()
                }

                // The wizard's "read this database's schema", which is exactly what a retry is here
                // — the schema is the only thing this screen loads.
                NotionSetupEvent.DatabaseConfirmed -> {
                    load()
                }

                else -> {
                    Unit
                }
            }
        }

        /**
         * Reads the current schema and lays the saved mapping over it.
         *
         * **The schema is re-read every time rather than cached**, because the reason to open this
         * screen is that Notion has changed. A stale copy would offer the user the columns that
         * existed the last time they connected, which is the one thing this screen exists to fix.
         */
        private fun load() {
            busy {
                val saved = repository.savedMapping()
                if (saved == null) {
                    // Unreachable from the UI — the route is only offered to a configured app — but
                    // a disconnect racing this screen would land here, and a blank screen with no
                    // explanation is worse than the sentence that is already written for it.
                    _uiState.update { it.copy(failure = SyncFailure.NotConnected) }
                    return@busy
                }

                when (val result = repository.loadProperties(saved.sourceId)) {
                    is NotionSetupResult.Success -> {
                        _uiState.update {
                            it
                                .copy(selectedDatabaseId = saved.sourceId)
                                .withMapping(properties = result.value, saved = saved)
                        }
                    }

                    is NotionSetupResult.Failure -> {
                        _uiState.update { it.copy(failure = result.reason) }
                    }

                    NotionSetupResult.UnreadableLink -> {
                        Unit
                    }
                }
            }
        }

        /**
         * Saves the mapping, then applies it in the foreground.
         *
         * ⚠ **The import is awaited rather than scheduled, and it has to be.** Until it runs, Room
         * still holds the old habit set — so popping straight back to a list that has not changed
         * would read as a save that did nothing. `CLAUDE.md` §1's "no screen waits on a sync to
         * render" is not violated: this screen is already rendered, and the wait is a button
         * spinner on an action the user just took.
         *
         * ⚠ **A failed import leaves the mapping saved**, which is correct — the next successful
         * sync applies it — but the screen stays put and says so, because the habit list behind it
         * is still the old one. Pressing the button again re-saves the same mapping, which is
         * idempotent, and retries.
         */
        @Suppress("ReturnCount")
        private fun save() {
            val state = _uiState.value
            val sourceId = state.selectedDatabaseId ?: return
            val dayKey = state.dayKeyPropertyId ?: return
            if (state.habitPropertyIds.isEmpty()) return

            busy {
                repository.saveMapping(
                    sourceId = sourceId,
                    dayKeyPropertyId = dayKey,
                    habitPropertyIds = state.habitPropertyIdsInOrder,
                )
                when (val result = repository.importNow()) {
                    is NotionSetupResult.Success -> _saved.send(Unit)
                    is NotionSetupResult.Failure -> _uiState.update { it.copy(failure = result.reason) }
                    NotionSetupResult.UnreadableLink -> Unit
                }
            }
        }

        /** [NotionSetupViewModel.busy]'s twin, and for its reason: the banner means "the last thing you tried". */
        private fun busy(block: suspend () -> Unit) {
            _uiState.update { it.copy(isBusy = true, failure = null) }
            viewModelScope.launch {
                block()
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }
