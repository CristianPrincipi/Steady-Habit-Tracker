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
 * Drives the token step **on its own**, for a connection that already exists (M12.7).
 *
 * ⭐⭐ **Why this exists at all, and it is ADR-079's argument a second time.** Until now the token
 * could only be entered by finishing first-time setup, because the wizard is a *gate* rather than a
 * route (ADR-032): `HabitsShell` shows it only while no mapping is stored. So a token **rotated or
 * revoked in Notion** — an ordinary thing to do — left the app failing with copy that said "paste
 * one to continue" and nowhere to paste it. The only way through was *disconnect → reconnect*, which
 * throws away a database choice and a property mapping that were never wrong.
 *
 * M7.8 built exactly this shape for the mapping and gave the reason: a screen that repairs one half
 * of the connection without destroying the other.
 *
 * ⚠ **It never touches the mapping**, which is the mirror of [EditMappingViewModel] never touching
 * the token. The only write here is [NotionSetupRepository.saveToken].
 *
 * ### ⚠⚠ The new token is saved *before* it is known to be good, and it has to be
 *
 * Every call in `NotionApi` reads the token from the store — that is what makes the token store the
 * one place a credential lives (`CLAUDE.md` §3.6). So there is no way to test a token without first
 * storing it, and the wizard's own first step has always worked this way.
 *
 * The consequence is stated rather than hidden: a mistyped token replaces whatever was there. That
 * is acceptable **here** precisely because of who arrives — this screen is reached from a connection
 * that is already failing, so the value being overwritten is one that does not work. It is why the
 * row that opens it is not offered as general-purpose maintenance.
 */
@HiltViewModel
class ReplaceTokenViewModel
    @Inject
    constructor(
        private val repository: NotionSetupRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(NotionSetupUiState(step = NotionSetupStep.Token))
        val uiState: StateFlow<NotionSetupUiState> = _uiState.asStateFlow()

        private val _saved = Channel<Unit>(Channel.BUFFERED)

        /**
         * Emits once a new token is stored **and proved against the user's own database**.
         *
         * A one-shot rather than a flag on the state (`CLAUDE.md` §6): the screen leaves when it
         * arrives, and a state that said "saved" would re-fire on the next recomposition.
         */
        val saved: Flow<Unit> = _saved.receiveAsFlow()

        /**
         * ⚠ **Two events, because the token step has two controls.**
         *
         * The `else` is the wizard's other nine, none of which anything on this screen can fire.
         */
        fun onEvent(event: NotionSetupEvent) {
            when (event) {
                is NotionSetupEvent.TokenChanged -> {
                    _uiState.update { it.copy(token = event.value) }
                }

                NotionSetupEvent.TokenSubmitted -> {
                    submit()
                }

                else -> {
                    Unit
                }
            }
        }

        /**
         * Save the token, then prove it by reading the schema of the database already chosen.
         *
         * ⭐ **`loadProperties` rather than `findSources`, and the difference is the point.** The
         * wizard searches for databases because it does not know which one yet; here one is already
         * mapped, so the honest question is not "what can this token see" but **"can this token
         * still read the database this app is set up against"** — which is also the question whose
         * failure `SyncFailure.DatabaseNotShared` answers correctly.
         */
        private fun submit() {
            val token = _uiState.value.token
            if (token.isBlank()) return

            viewModelScope.launch {
                _uiState.update { it.copy(isBusy = true, failure = null) }

                repository.saveToken(token)
                // Cleared here rather than on success, exactly as the wizard does it: the field's
                // job is done either way, and a rejected token is the thing being replaced.
                _uiState.update { it.copy(token = "") }

                val mapping = repository.savedMapping()
                if (mapping == null) {
                    // Unreachable from the UI — the row that opens this screen only exists on a
                    // configured app — but a disconnect racing this screen would land here, and a
                    // blank screen is worse than the sentence already written for this state.
                    _uiState.update { it.copy(isBusy = false, failure = SyncFailure.NotConnected) }
                    return@launch
                }

                when (val result = repository.loadProperties(mapping.sourceId)) {
                    is NotionSetupResult.Success -> {
                        _uiState.update { it.copy(isBusy = false) }
                        _saved.trySend(Unit)
                    }

                    else -> {
                        _uiState.update {
                            it.copy(isBusy = false, failure = result.failureOrNotConnected())
                        }
                    }
                }
            }
        }
    }

/**
 * The failure a non-success carries, or the one that describes having no usable token at all.
 *
 * `UnreadableLink` cannot arise here — nothing on this screen pastes a link — so it falls through to
 * the same sentence as a missing token rather than inventing a case for it.
 */
private fun NotionSetupResult<*>.failureOrNotConnected(): SyncFailure =
    (this as? NotionSetupResult.Failure)?.reason ?: SyncFailure.NotConnected
