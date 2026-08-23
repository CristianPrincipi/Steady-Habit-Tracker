package com.cristianprincipi.habits.feature.notionsetup

/**
 * Everything the user can do on the setup screen.
 *
 * One sealed interface and one `onEvent`, per `CLAUDE.md` §6 — rather than a lambda per control
 * piled onto the ViewModel. With a wizard this earns its keep twice over: the same primary button
 * means three different things depending on the step, and a sealed set makes that a `when` the
 * compiler checks instead of three nullable callbacks.
 *
 * **There is no `Retry`.** A failure leaves the primary action enabled, so trying again is pressing
 * the same button — which is also what the user would do without being told.
 */
sealed interface NotionSetupEvent {
    /** Every keystroke in the token field. */
    data class TokenChanged(
        val value: String,
    ) : NotionSetupEvent

    /** Save the token and find what it can reach. */
    data object TokenSubmitted : NotionSetupEvent

    /** Reveal the paste-a-link field — the fallback when the search has not found the database. */
    data object LinkFieldOpened : NotionSetupEvent

    data class DatabaseLinkChanged(
        val value: String,
    ) : NotionSetupEvent

    /** Resolve the pasted link to the databases inside it. */
    data object DatabaseLinkSubmitted : NotionSetupEvent

    data class DatabaseSelected(
        val id: String,
    ) : NotionSetupEvent

    /** Read the chosen database's schema and move to the mapping step. */
    data object DatabaseConfirmed : NotionSetupEvent

    data class DayKeySelected(
        val propertyId: String,
    ) : NotionSetupEvent

    /** Tick or untick one checkbox column as a habit. */
    data class HabitToggled(
        val propertyId: String,
    ) : NotionSetupEvent

    /** Save the mapping and run the first import. */
    data object MappingSubmitted : NotionSetupEvent

    /** One step back. Never leaves [NotionSetupStep.Token] — there is nothing behind it yet. */
    data object BackPressed : NotionSetupEvent
}
