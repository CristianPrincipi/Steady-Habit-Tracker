package com.cristianprincipi.habits.feature.notionsetup

import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.domain.model.SavedMeasure
import com.cristianprincipi.habits.domain.model.SavedNotionMapping
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
    /** The number columns ticked as measures (M15.4). ⚠ Never pre-ticked — see [withMapping]. */
    val measurePropertyIds: Set<String> = emptySet(),
    /**
     * What is in each measure's unit field right now, keyed by property id.
     *
     * ⭐ **Keyed rather than paired with the tick**, and kept when the box is unticked: a user who
     * unticks `Weight` to read the warning and ticks it straight back should not have to retype
     * `kg`. Only ticked ids reach [measuresInOrder], so an orphaned entry costs nothing.
     *
     * A missing key and a blank string mean the same thing — no unit — which is why nothing here
     * distinguishes them.
     */
    val measureUnits: Map<String, String> = emptyMap(),
    /**
     * What was mapped **before** this screen opened, for [habitsBeingDropped] to compare against.
     *
     * Empty on a first run, and empty against a different database — in both cases there is nothing
     * to stop syncing, so the warning has nothing to say. Already intersected with the current
     * candidates, exactly as [habitPropertyIds] is, so a habit whose column is gone upstream is not
     * reported as one the user is about to drop.
     */
    val previouslyMappedHabitIds: Set<String> = emptySet(),
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

    /** The number columns, which are what a measure can be (M15.4). */
    val measureCandidates: List<NotionProperty>
        get() = properties.filter { it.role == NotionPropertyRole.Measure }

    /**
     * Everything that can be none of the three, shown anyway.
     *
     * `Area` and `Progress` are the user's own data in the same database
     * (`docs/notion-schema.md` §7), and a column they can see in Notion but not here reads as a bug.
     * Showing them with their type answers "why can't I pick Progress?" on the screen itself.
     *
     * ⚠ **This paragraph used to name `Weight` and `Sleep` too**, and M15.4 moved them into
     * [measureCandidates]. The sentence they leave behind is unchanged in meaning: a column that is
     * here is still one the app will read and never touch.
     */
    val unavailableProperties: List<NotionProperty>
        get() = properties.filter { it.role == NotionPropertyRole.Unavailable }

    /**
     * ⭐ The habits this selection would take out of the set — **the one destructive-looking thing
     * either mapping screen can do** (M7.8).
     *
     * Unticking a habit stops it syncing and removes it from every screen (ADR-081). Its history is
     * kept and comes back whole if the property is ticked again, but from the user's side it looks
     * like deleting a habit and a year with it, so the screen says so — **named, and only when it
     * applies**, which was the user's call on 2026-08-31.
     *
     * In candidate order rather than tick order, so the sentence lists habits in the order they
     * appear above it.
     */
    val habitsBeingDropped: List<NotionProperty>
        get() =
            habitCandidates.filter { it.id in previouslyMappedHabitIds && it.id !in habitPropertyIds }

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

    /**
     * The habit ids to save, **in candidate order rather than tick order**.
     *
     * The user reads the list top to bottom, so this is the order they saw when they ticked.
     *
     * ⚠⚠ **This used to say "and this becomes `HabitEntity.sortOrder`", which M15.5 made false
     * and which was the reason the habit order could not be the user's.** Candidate order is
     * `loadProperties`' name sort — alphabetical by Notion property name, which for names that
     * begin with an emoji means sorted by that emoji's code point. Every save wrote that over
     * whatever order the mapping held.
     *
     * `sortOrder` is now app-local, carried forward by `HabitImporter` the way `color` is, and
     * changed on the manage-habits screen (ADR-127). So this decides the **seed** position of a
     * habit ticked for the first time and nothing else: an existing habit keeps where the user put
     * it, whatever this returns.
     */
    val habitPropertyIdsInOrder: List<String>
        get() = habitCandidates.map(NotionProperty::id).filter { it in habitPropertyIds }

    /**
     * The measures to save, in candidate order, each carrying whatever unit was typed for it.
     *
     * ⚠ `.trim()` here and nowhere else. The unit is a **label of ours**, not Notion data — unlike
     * a property name, which `docs/notion-schema.md` §2.3 requires be stored verbatim and never
     * normalised. A stray space before `kg` would draw as `84.2  kg` for ever.
     */
    val measuresInOrder: List<SavedMeasure>
        get() =
            measureCandidates
                .map(NotionProperty::id)
                .filter { it in measurePropertyIds }
                .map { id -> SavedMeasure(propertyId = id, unit = measureUnits[id].orEmpty().trim()) }
}

/**
 * The mapping step, populated from a schema and whatever was mapped before it.
 *
 * ⭐ **Pre-tick on the first run only** — the user's decision, 2026-08-23 (ADR-051).
 *
 * A first run, or a run against a *different* database, starts with every checkbox ticked: in the
 * database this app was built for, every checkbox column is a habit, so that step becomes one tap.
 * A repeat run starts from exactly what was mapped before, which means a column added in Notion
 * since then arrives **unticked** — the moment the user wants to be asked rather than assumed at.
 * Assuming would quietly turn a `Reviewed?` flag into a habit the app then writes `false` into.
 *
 * The saved selection is intersected with what the schema currently offers, so a habit whose column
 * was deleted upstream does not come back as a tick against nothing.
 *
 * ⚠ **Here rather than in a ViewModel because there are two of them** (M7.8). The wizard reaches
 * this step through [NotionSetupViewModel]; the edit-mapping screen reaches the same step through
 * [EditMappingViewModel], and a second copy of these rules is a second place for them to drift.
 *
 * @param saved what is already mapped **for this same database**, or null — a first run, or a
 *   different database, which is the same thing as far as the ticks are concerned.
 */
internal fun NotionSetupUiState.withMapping(
    properties: List<NotionProperty>,
    saved: SavedNotionMapping?,
): NotionSetupUiState {
    val candidateIds = properties.filter { it.role == NotionPropertyRole.Habit }.map(NotionProperty::id)
    val previouslyMapped =
        saved?.habitPropertyIds?.filterTo(mutableSetOf()) { id -> id in candidateIds }.orEmpty()
    val dayKeyCandidates = properties.filter { it.role == NotionPropertyRole.DayKey }
    // Intersected with the current candidates for the same reason the habits are: a measure whose
    // column was deleted or retyped upstream must not come back as a tick against nothing.
    val measureCandidateIds = properties.filter { it.role == NotionPropertyRole.Measure }.map(NotionProperty::id)
    val savedMeasures = saved?.measures.orEmpty().filter { it.propertyId in measureCandidateIds }

    return copy(
        properties = properties,
        // The previously chosen day key if it is still a date column, else the only date column
        // there is, else nothing. Choosing when there is exactly one candidate is not a guess — it
        // is the only possible answer, and it is visibly selected for the user to disagree with.
        dayKeyPropertyId =
            saved
                ?.dayKeyPropertyId
                ?.takeIf { id -> dayKeyCandidates.any { it.id == id } }
                ?: dayKeyCandidates.singleOrNull()?.id,
        habitPropertyIds = if (saved == null) candidateIds.toSet() else previouslyMapped,
        previouslyMappedHabitIds = previouslyMapped,
        // ⚠⚠ **Measures are NEVER pre-ticked, not even on a first run**, and that is not an
        // oversight of the rule above — it is the same rule reaching the opposite answer.
        // Pre-ticking every checkbox is safe because of a fact about *this* database: every
        // checkbox column in it is a habit, so the assumption is right every time. No such fact
        // holds for numbers. A number column could be a target, a page count, an ID, a rating;
        // "average it on the Stats screen" is a claim only the user can make, and a wrong guess
        // here puts a meaningless tile on a screen and stores a column of numbers nobody asked for.
        measurePropertyIds = savedMeasures.map(SavedMeasure::propertyId).toSet(),
        measureUnits = savedMeasures.associate { it.propertyId to it.unit },
    )
}

/** Tick or untick one number column as a measure. Shared for [withMapping]'s reason. */
internal fun NotionSetupUiState.withMeasureToggled(propertyId: String): NotionSetupUiState =
    copy(
        measurePropertyIds =
            if (propertyId in measurePropertyIds) {
                measurePropertyIds - propertyId
            } else {
                measurePropertyIds + propertyId
            },
    )

/** Every keystroke in one measure's unit field. Stored untrimmed; [measuresInOrder] trims. */
internal fun NotionSetupUiState.withMeasureUnit(
    propertyId: String,
    unit: String,
): NotionSetupUiState = copy(measureUnits = measureUnits + (propertyId to unit))

/** Tick or untick one checkbox column. Shared for [withMapping]'s reason. */
internal fun NotionSetupUiState.withHabitToggled(propertyId: String): NotionSetupUiState =
    copy(
        habitPropertyIds =
            if (propertyId in habitPropertyIds) {
                habitPropertyIds - propertyId
            } else {
                habitPropertyIds + propertyId
            },
    )
