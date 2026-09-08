package com.cristianprincipi.habits.feature.notionsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.ui.launchWrite
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
import kotlinx.coroutines.flow.first
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
            resumeWhereAStoredTokenLeftOff()
            resetWhenDisconnected()
        }

        /**
         * ⭐⭐ **M13.7 — picks the wizard up where a killed process left it, rather than asking for
         * the token again.**
         *
         * ### Why this is a lock-out and not an inconvenience
         *
         * [NotionSetupRepository.saveToken] stores the token at the **first** step, two steps before
         * the user has chosen anything — that repository's own KDoc says so, and warns against
         * treating "has a token" as "is set up". Meanwhile `CLAUDE.md` §3.3 sends the user *out of
         * the app*, into Notion, to share the database with the connection. **Backgrounding the app
         * mid-wizard is therefore the documented flow, not an edge case**, and a process reclaimed
         * while they are away used to bring them back to "paste your token" — with the app holding a
         * perfectly good one it will never show anybody (§3.6), and ADR-107 recording that Notion
         * shows a token once. The wizard is a gate around the whole app (ADR-032), so there was no
         * way past it.
         *
         * ### ⚠⚠ Why the fix is not `SavedStateHandle`
         *
         * The obvious answer to "survive process death" is to save the state into the
         * saved-instance-state `Bundle`. **That bundle is plaintext and system-persisted**, so for
         * this screen it would mean writing a Notion token — which §3.6 calls a password — into
         * exactly the storage the encrypted token store exists to avoid. The state worth restoring
         * is already on disk, encrypted, and the repository already exposes the one bit needed:
         * whether a token exists. `NotionSetupUiState.token` stays unsaved, deliberately.
         *
         * ### What it does on each of the three paths
         *
         * - **No stored token** — a first run. Returns before spending anything, so the token step
         *   still opens with no request made and no busy flag flicker.
         * - **A stored token that works** — advances to the database step through the very same
         *   [thenShowDatabases] a fresh paste uses, so a resumed wizard and a typed one cannot drift.
         * - **A stored token that no longer works** — revoked or rotated upstream. [thenShowDatabases]
         *   leaves the step where it is and puts the reason on screen, so the user lands on the token
         *   step *being told why* rather than being asked again for no stated reason. That is the one
         *   case where asking again is right.
         *
         * ⚠ It reads [NotionSetupRepository.isConnected] once rather than collecting it: this is a
         * question about how the app was found on launch, not a condition to keep watching.
         * [resetWhenDisconnected] is the one that watches, and it watches the other flow.
         *
         * ⚠ **`ReplaceTokenViewModel` deliberately does not do this.** It renders the same step for
         * a connection that already exists, and its whole purpose is to take a *new* token — so
         * skipping the field because one is stored would skip the screen's only reason to exist.
         */
        private fun resumeWhereAStoredTokenLeftOff() {
            // ⚠ Quiet on failure (M13.12). If the stored token cannot be read the wizard simply
            // opens at step one, which is the same screen a genuinely fresh install gets — and is
            // what the user needs to do anyway. `busy` below owns the failures that happen after
            // this point, and it has a banner for them.
            launchWrite {
                if (!repository.isConnected.first()) return@launchWrite

                busy { repository.findSources().thenShowDatabases() }
            }
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
            // ⚠ The one collector here rather than a write, and quiet on failure for that reason:
            // there is nothing to say and nobody to say it to. If the flow fails, this watcher stops
            // and a later disconnect leaves the wizard showing a stale `Done` — the M4.18 bug above,
            // which was survivable. Crashing the app on the same fault is not (M13.12).
            launchWrite {
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

                is NotionSetupEvent.MeasureToggled -> {
                    _uiState.update { it.withMeasureToggled(event.propertyId) }
                }

                is NotionSetupEvent.MeasureUnitChanged -> {
                    _uiState.update { it.withMeasureUnit(event.propertyId, event.value) }
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
         * Moves to the mapping step, populated by [withMapping].
         *
         * The rules that populate it are in that function rather than here, because M7.8's
         * edit-mapping screen is the same step reached without the wizard and has to obey them
         * identically.
         */
        private suspend fun showMapping(
            sourceId: String,
            properties: List<NotionProperty>,
        ) {
            val saved = repository.savedMapping()?.takeIf { it.sourceId == sourceId }
            _uiState.update {
                it.withMapping(properties = properties, saved = saved).copy(step = NotionSetupStep.MapProperties)
            }
        }

        private fun toggleHabit(propertyId: String) {
            _uiState.update { it.withHabitToggled(propertyId) }
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
                    habitPropertyIds = state.habitPropertyIdsInOrder,
                    measures = state.measuresInOrder,
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
            // ⚠⚠ **The busy flag is cleared on BOTH paths, and that is the load-bearing half of
            // M13.12 here.** A throw used to take the app down; catching it without this would have
            // left `isBusy` true for ever — a spinner over a screen that has stopped doing anything,
            // which is a worse dead end than the crash. The banner is deliberately not set: every
            // failure it names is something Notion said, and this is the device refusing to write.
            launchWrite(onFailure = { _uiState.update { it.copy(isBusy = false) } }) {
                block()
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }
