package com.cristianprincipi.habits.feature.notionsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.domain.model.SavedNotionMapping
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the connection wizard.
 *
 * **It holds no `Context`, no `WorkManager` and no Notion type** — only
 * [NotionSetupRepository] (`CLAUDE.md` §6). Everything it renders is a domain model, and every
 * failure it shows is a `SyncFailure`, which is the same vocabulary M4.13's sync-status screen uses,
 * so the two cannot end up calling the same 401 different things.
 *
 * ⚠ **The token is in the state only while it is being typed.** [NotionSetupEvent.TokenSubmitted]
 * saves it and clears the field in the same update. `CLAUDE.md` §3.6.
 */
@HiltViewModel
class NotionSetupViewModel
    @Inject
    constructor(
        private val repository: NotionSetupRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(NotionSetupUiState())
        val uiState: StateFlow<NotionSetupUiState> = _uiState.asStateFlow()

        init {
            resetWhenDisconnected()
        }

        /**
         * ⭐ **Starts the wizard over when the app stops being configured** — a disconnect.
         *
         * Found by watching a real disconnect in M4.18, not by a test. This ViewModel outlives the
         * screen: `HabitsNavHost` gates the wizard with an `if` rather than making it a destination,
         * so there is no back stack entry to scope to and `hiltViewModel()` resolves against the
         * *activity*. The instance therefore survives being swapped away, and a disconnect brought
         * the wizard back holding [NotionSetupStep.Done] from the previous run — a dead end reading
         * "Connected, 513 days imported" at someone who had just disconnected, with no control on it
         * and [goBack] treating `Done` as terminal. The only way out was to leave the app.
         *
         * Watching the *transition* rather than the value matters: `isConfigured` is false for the
         * whole of a normal first run, and resetting on that would wipe the wizard mid-flow.
         *
         * **This does not belong in the host** — `HabitsAppRoot` was deleted in M5.2 and the gate
         * moved to `HabitsNavHost`, which is exactly why it is here. State that claims a connection
         * the app no longer has is wrong wherever the screen is hosted from.
         */
        private fun resetWhenDisconnected() {
            viewModelScope.launch {
                var wasConfigured = false
                repository.isConfigured.distinctUntilChanged().collect { configured ->
                    if (wasConfigured && !configured) {
                        _uiState.value = NotionSetupUiState()
                    }
                    wasConfigured = configured
                }
            }
        }

        fun onEvent(event: NotionSetupEvent) {
            when (event) {
                is NotionSetupEvent.TokenChanged -> {
                    _uiState.update { it.copy(token = event.value) }
                }

                NotionSetupEvent.TokenSubmitted -> {
                    submitToken()
                }

                NotionSetupEvent.LinkFieldOpened -> {
                    _uiState.update { it.copy(isLinkFieldOpen = true) }
                }

                is NotionSetupEvent.DatabaseLinkChanged -> {
                    _uiState.update { it.copy(databaseLink = event.value, isLinkUnreadable = false) }
                }

                NotionSetupEvent.DatabaseLinkSubmitted -> {
                    submitLink()
                }

                is NotionSetupEvent.DatabaseSelected -> {
                    _uiState.update { it.copy(selectedDatabaseId = event.id) }
                }

                NotionSetupEvent.DatabaseConfirmed -> {
                    confirmDatabase()
                }

                is NotionSetupEvent.DayKeySelected -> {
                    _uiState.update { it.copy(dayKeyPropertyId = event.propertyId) }
                }

                is NotionSetupEvent.HabitToggled -> {
                    toggleHabit(event.propertyId)
                }

                NotionSetupEvent.MappingSubmitted -> {
                    submitMapping()
                }

                NotionSetupEvent.BackPressed -> {
                    goBack()
                }
            }
        }

        /**
         * Saves the token, then spends a call finding out whether it works.
         *
         * Saving first is not optimism — the client reads the token from the store, so there is no
         * way to try one without storing it. A token that turns out to be rejected stays stored and
         * is simply overwritten by the next attempt.
         */
        private fun submitToken() {
            val token = _uiState.value.token
            if (token.isBlank()) return

            busy {
                repository.saveToken(token)
                // Cleared here rather than on success: the field's job is done either way, and a
                // rejected token is not worth keeping on screen — it is the thing to replace.
                _uiState.update { it.copy(token = "") }
                repository.findSources().thenShowDatabases()
            }
        }

        private fun submitLink() {
            val link = _uiState.value.databaseLink
            if (link.isBlank()) return

            busy {
                when (val result = repository.findSourcesInDatabase(link)) {
                    // Nothing was sent, so this is a field-level error rather than a banner about
                    // Notion. Rendering the sharing instruction here would be the wrong sentence.
                    NotionSetupResult.UnreadableLink -> _uiState.update { it.copy(isLinkUnreadable = true) }

                    else -> result.thenShowDatabases()
                }
            }
        }

        /**
         * Moves to the database step, preselecting the only candidate when there is only one.
         *
         * A list of one with nothing selected is a question with one possible answer, and making the
         * user tap it teaches them nothing.
         */
        private fun NotionSetupResult<List<NotionSource>>.thenShowDatabases() {
            when (this) {
                is NotionSetupResult.Success -> {
                    _uiState.update {
                        it.copy(
                            step = NotionSetupStep.ChooseDatabase,
                            databases = value,
                            selectedDatabaseId = it.selectedDatabaseId ?: value.singleOrNull()?.id,
                        )
                    }
                }

                is NotionSetupResult.Failure -> {
                    _uiState.update { it.copy(failure = reason) }
                }

                NotionSetupResult.UnreadableLink -> {
                    _uiState.update { it.copy(isLinkUnreadable = true) }
                }
            }
        }

        private fun confirmDatabase() {
            val sourceId = _uiState.value.selectedDatabaseId ?: return

            busy {
                when (val result = repository.loadProperties(sourceId)) {
                    is NotionSetupResult.Success -> showMapping(sourceId, result.value)
                    is NotionSetupResult.Failure -> _uiState.update { it.copy(failure = result.reason) }
                    NotionSetupResult.UnreadableLink -> Unit
                }
            }
        }

        /**
         * ⭐ **Pre-tick on the first run only** — the user's decision, 2026-08-23 (ADR-051).
         *
         * A first run, or a run against a *different* database, starts with every checkbox ticked:
         * in the database this app was built for, every checkbox column is a habit, so that step
         * becomes one tap. A repeat run starts from exactly what was mapped before, which means a
         * column added in Notion since then arrives **unticked** — the moment the user wants to be
         * asked rather than assumed at. Assuming would quietly turn a `Reviewed?` flag into a habit
         * the app then writes `false` into.
         *
         * The saved selection is intersected with what the schema currently offers, so a habit whose
         * column was deleted upstream does not come back as a tick against nothing.
         */
        private suspend fun showMapping(
            sourceId: String,
            properties: List<NotionProperty>,
        ) {
            val saved = repository.savedMapping()?.takeIf { it.sourceId == sourceId }
            val candidateIds =
                properties
                    .filter { it.role == NotionPropertyRole.Habit }
                    .map(NotionProperty::id)

            _uiState.update {
                it.copy(
                    step = NotionSetupStep.MapProperties,
                    properties = properties,
                    dayKeyPropertyId = saved.dayKeyOrOnlyCandidate(properties),
                    habitPropertyIds =
                        if (saved == null) {
                            candidateIds.toSet()
                        } else {
                            saved.habitPropertyIds.filterTo(mutableSetOf()) { id -> id in candidateIds }
                        },
                )
            }
        }

        /**
         * The previously chosen day key if it is still a date column, else the only date column
         * there is, else nothing.
         *
         * Choosing when there is exactly one candidate is not a guess — it is the only possible
         * answer, and it is visibly selected for the user to disagree with.
         */
        private fun SavedNotionMapping?.dayKeyOrOnlyCandidate(properties: List<NotionProperty>): String? {
            val candidates = properties.filter { it.role == NotionPropertyRole.DayKey }
            return this
                ?.dayKeyPropertyId
                ?.takeIf { id -> candidates.any { it.id == id } }
                ?: candidates.singleOrNull()?.id
        }

        private fun toggleHabit(propertyId: String) {
            _uiState.update { state ->
                val habits = state.habitPropertyIds
                state.copy(
                    habitPropertyIds = if (propertyId in habits) habits - propertyId else habits + propertyId,
                )
            }
        }

        // ReturnCount: three guard clauses over state the button is already disabled for. They are
        // belt and braces against a stale event, not branching — a `when` would be longer and say
        // less.
        @Suppress("ReturnCount")
        private fun submitMapping() {
            val state = _uiState.value
            val sourceId = state.selectedDatabaseId ?: return
            val dayKey = state.dayKeyPropertyId ?: return
            if (state.habitPropertyIds.isEmpty()) return

            busy {
                repository.saveMapping(
                    sourceId = sourceId,
                    dayKeyPropertyId = dayKey,
                    // The candidate order, not the tick order: the user reads the list top to bottom
                    // and expects their habits in that order, and this becomes HabitEntity.sortOrder.
                    habitPropertyIds =
                        state.habitCandidates
                            .map(NotionProperty::id)
                            .filter { it in state.habitPropertyIds },
                )
                when (val result = repository.importNow()) {
                    is NotionSetupResult.Success -> {
                        _uiState.update { it.copy(step = NotionSetupStep.Done, importedDays = result.value) }
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
         * One step back, clearing what the step ahead had decided.
         *
         * Going back from the mapping step drops the properties and the selection rather than
         * keeping them: the reason to go back is to choose a different database, and a mapping left
         * over from the previous one would be ticks against columns that no longer exist.
         */
        private fun goBack() {
            _uiState.update { state ->
                when (state.step) {
                    NotionSetupStep.Token -> {
                        state
                    }

                    NotionSetupStep.ChooseDatabase -> {
                        state.copy(step = NotionSetupStep.Token, failure = null, databases = emptyList())
                    }

                    NotionSetupStep.MapProperties -> {
                        state.copy(
                            step = NotionSetupStep.ChooseDatabase,
                            failure = null,
                            properties = emptyList(),
                            dayKeyPropertyId = null,
                            habitPropertyIds = emptySet(),
                        )
                    }

                    NotionSetupStep.Done -> {
                        state
                    }
                }
            }
        }

        /**
         * Runs [block] with the busy flag on and the previous failure cleared.
         *
         * Clearing on the way in rather than the way out is what makes the banner mean "the last
         * thing you tried failed" rather than "something failed once".
         */
        private fun busy(block: suspend () -> Unit) {
            _uiState.update { it.copy(isBusy = true, failure = null) }
            viewModelScope.launch {
                block()
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }
