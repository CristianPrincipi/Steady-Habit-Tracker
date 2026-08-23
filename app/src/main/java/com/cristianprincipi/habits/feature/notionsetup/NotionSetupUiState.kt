package com.cristianprincipi.habits.feature.notionsetup

import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.domain.model.SyncFailure

/**
 * Where the user is in the connection flow.
 *
 * **A wizard, one decision per screen** — confirmed by the user on 2026-08-23. The prototype designs
 * a single Settings card stack and never designs the two steps after the token, so this shape was a
 * decision to make rather than one to read off the design (`docs/design-spec.md` §6). The reasoning
 * is in ADR-051: connecting happens once, the mapping step wants the whole screen, and changing the
 * database after mapping is honest here — you go back, and the mapping rebuilds from the new schema.
 */
enum class NotionSetupStep {
    /** Paste the token. Nothing has been sent yet. */
    Token,

    /** Choose among the databases the connection can reach. */
    ChooseDatabase,

    /** Say which column is the day and which are habits. */
    MapProperties,

    /** The import finished. */
    Done,
    ;

    /** 1-based, for "step N of 3". [Done] is not a step you are asked to do, so it counts as the last. */
    val number: Int get() = minOf(ordinal + 1, LAST_STEP)

    companion object {
        /** [Token], [ChooseDatabase], [MapProperties] — [Done] is the end, not a fourth thing to do. */
        const val LAST_STEP = 3
    }
}

/**
 * Everything the setup screen renders, and nothing else.
 *
 * ⚠ **There is no token in here after it is submitted, and there must never be one that persists.**
 * [token] holds what is currently in the text field, because a text field has to hold something; it
 * is cleared the moment the token is saved. `CLAUDE.md` §3.6 and
 * [com.cristianprincipi.habits.data.remote.notion.auth.NotionTokenStore]'s own warning: a screen
 * that keeps the token in its state has put it somewhere that will eventually be printed.
 *
 * The derived properties at the bottom exist so the composables branch on a name rather than on a
 * condition. A button that reads `enabled = state.canContinueFromToken` cannot drift from the rule;
 * one that reads `enabled = state.token.isNotBlank() && !state.isBusy` is a rule in two places.
 */
data class NotionSetupUiState(
    val step: NotionSetupStep = NotionSetupStep.Token,
    /** What is in the token field right now. Cleared as soon as it has been saved. */
    val token: String = "",
    /** What is in the paste-a-link field, shown only when [isLinkFieldOpen]. */
    val databaseLink: String = "",
    val isLinkFieldOpen: Boolean = false,
    /** True when the pasted link had nothing id-shaped in it — a field-level error, not a banner. */
    val isLinkUnreadable: Boolean = false,
    val databases: List<NotionSource> = emptyList(),
    val selectedDatabaseId: String? = null,
    val properties: List<NotionProperty> = emptyList(),
    val dayKeyPropertyId: String? = null,
    val habitPropertyIds: Set<String> = emptySet(),
    /** A request is in flight. Disables the primary action and swaps its label for a spinner. */
    val isBusy: Boolean = false,
    /** The last failure, as a sentence the screen can render. Cleared when the user tries again. */
    val failure: SyncFailure? = null,
    /** Days brought in by the first import, once [NotionSetupStep.Done] is reached. */
    val importedDays: Int = 0,
) {
    /** The date columns, which are what a day key can be. */
    val dayKeyCandidates: List<NotionProperty>
        get() = properties.filter { it.role == NotionPropertyRole.DayKey }

    /** The checkbox columns, which are what a habit can be. */
    val habitCandidates: List<NotionProperty>
        get() = properties.filter { it.role == NotionPropertyRole.Habit }

    /**
     * Everything that can be neither, shown anyway.
     *
     * `Weight`, `Sleep`, `Area` and `Progress` are the user's own data in the same database
     * (`docs/notion-schema.md` §7), and a column they can see in Notion but not here reads as a bug.
     * Showing them with their type answers "why can't I pick Progress?" on the screen itself.
     */
    val unavailableProperties: List<NotionProperty>
        get() = properties.filter { it.role == NotionPropertyRole.Unavailable }

    val canContinueFromToken: Boolean get() = token.isNotBlank() && !isBusy

    val canLookUpLink: Boolean get() = databaseLink.isNotBlank() && !isBusy

    val canContinueFromDatabase: Boolean get() = selectedDatabaseId != null && !isBusy

    /**
     * A mapping needs a day key and at least one habit.
     *
     * An empty habit set is representable — `NotionPropertyMapping` allows it, and says the screen
     * that collects them is where the rule belongs. This is that rule: finishing setup having chosen
     * no habits produces an app that syncs nothing and looks broken.
     */
    val canSaveMapping: Boolean
        get() = dayKeyPropertyId != null && habitPropertyIds.isNotEmpty() && !isBusy
}
